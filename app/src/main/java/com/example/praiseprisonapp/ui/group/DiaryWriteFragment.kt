package com.example.praiseprisonapp.ui.group

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.praiseprisonapp.R
import com.example.praiseprisonapp.data.api.WeatherApiClient
import com.example.praiseprisonapp.data.api.WeatherInfo
import com.example.praiseprisonapp.data.model.DiaryData
import com.example.praiseprisonapp.databinding.DiaryWriteBinding
import com.example.praiseprisonapp.util.LocationConverter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import android.location.Geocoder
import androidx.core.view.isVisible
import com.example.praiseprisonapp.data.api.WeatherData

import com.google.android.gms.tasks.CancellationTokenSource

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.praiseprisonapp.network.ApiClient
import com.example.praiseprisonapp.data.model.Advice
import com.example.praiseprisonapp.util.SentimentAnalyzer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AlertDialog

class DiaryWriteFragment : Fragment() {
    private var _binding: DiaryWriteBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var locationRequest: LocationRequest? = null

    private var selectedImageUri: Uri? = null
    private var tempPhotoUri: Uri? = null
    private var selectedMood: String? = null
    private var currentWeatherInfo: WeatherInfo? = null
    private lateinit var groupId: String
    private lateinit var progressBar: View
    private lateinit var sentimentAnalyzer: SentimentAnalyzer


    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                getLastLocation()
            }
            else -> {
                Toast.makeText(context, "날씨 정보를 표시하려면 위치 권한이 필요합니다", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 권한 요청을 위한 launcher 추가
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(context, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestGalleryPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openGallery()
        } else {
            Toast.makeText(context, "갤러리 접근 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                showImagePreview(uri)
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            tempPhotoUri?.let { uri ->
                selectedImageUri = uri
                showImagePreview(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            groupId = it.getString("group_id") ?: throw IllegalArgumentException("Group ID is required")
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        setupLocationRequest()
        sentimentAnalyzer = SentimentAnalyzer.getInstance(requireContext())
    }

    private fun setupLocationRequest() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val (nx, ny) = LocationConverter.convertToGrid(location.latitude, location.longitude)
                    fetchWeatherInfo(nx, ny)
                    stopLocationUpdates()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DiaryWriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressBar = binding.progressBar

        setupToolbar()
        loadAdvice()
        setupMoodSelection()
        setupImageButtons()
        setupContentField()
        setupSendButton()
    }

    private fun setupToolbar() {
        val currentUser = auth.currentUser
        val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        val today = dateFormat.format(Date())

        currentUser?.let { user ->
            binding.tvUserName.text = "나"
        }
        
        binding.tvTitle.text = today

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        checkLocationPermission()
    }

    fun loadAdvice() {
        ApiClient.instance.getRandomAdvice().enqueue(object : Callback<Advice> {
            override fun onResponse(call: Call<Advice>, response: Response<Advice>) {
                val advice = response.body()
                if (advice != null) {
                    binding.adviceTextView.text = "\"${advice.message}\"\n— ${advice.author}"
                } else {
                    Toast.makeText(requireContext(), "명언을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Advice>, t: Throwable) {
                Toast.makeText(requireContext(), "API 오류: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getLastLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Toast.makeText(context, "날씨 정보를 표시하려면 위치 권한이 필요합니다", Toast.LENGTH_LONG).show()
                requestLocationPermission.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
            else -> {
                requestLocationPermission.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    Log.d("PraisePrison", "📍 현재 위치: 위도=${it.latitude}, 경도=${it.longitude}")
                    val (nx, ny) = LocationConverter.convertToGrid(it.latitude, it.longitude)
                    fetchWeatherInfo(nx, ny)
                } ?: run {
                    startLocationUpdates()
                }
            }
            .addOnFailureListener {
                startLocationUpdates()
            }
    }

    private fun startLocationUpdates() {
        try {
            Log.d("Location", "Starting location updates with request: $locationRequest")
            fusedLocationClient.requestLocationUpdates(
                locationRequest!!,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("Location", "Error requesting location updates", e)
        }
    }

    private fun stopLocationUpdates() {
        Log.d("Location", "Stopping location updates")
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    private fun fetchWeatherInfo(nx: Int, ny: Int) {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val now = Calendar.getInstance()
        
        // 3시간 단위로 baseTime 설정
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val baseTime = when {
            hour < 2 -> "2300"
            hour < 5 -> "0200"
            hour < 8 -> "0500"
            hour < 11 -> "0800"
            hour < 14 -> "1100"
            hour < 17 -> "1400"
            hour < 20 -> "1700"
            hour < 23 -> "2000"
            else -> "2300"
        }

        // 만약 baseTime이 2300이고 현재 시각이 00~02시 사이면 어제 날짜 사용
        if (baseTime == "2300" && hour < 2) {
            now.add(Calendar.DAY_OF_MONTH, -1)
        }

        val baseDate = dateFormat.format(now.time)

        // 위치 정보 디버깅을 위한 로그 추가
        Log.d("PraisePrison", "📍 위치 정보 요청 시작")
        
        // 위치 권한 다시 확인
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                try {
                    // 위치 요청 설정
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                        .setWaitForAccurateLocation(true)  // 정확한 위치를 기다림
                        .setMinUpdateDistanceMeters(100f)  // 최소 업데이트 거리
                        .setMaxUpdateDelayMillis(5000)     // 최대 업데이트 지연 시간
                        .build()

                    // 현재 위치 가져오기
                    val cancellationToken = CancellationTokenSource().token
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken)
                        .addOnSuccessListener { location: Location? ->
                            if (location != null) {
                                Log.d("PraisePrison", "📍 현재 위치: 위도=${location.latitude}, 경도=${location.longitude}, 정확도=${location.accuracy}m")
                                
                                // Geocoder로 주소 정보 가져오기
                                try {
                                    val geocoder = Geocoder(requireContext(), Locale.KOREA)
                                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                                    
                                    if (!addresses.isNullOrEmpty()) {
                                        val address = addresses[0]
                                        Log.d("PraisePrison", "📍 주소: ${address.getAddressLine(0)}")
                                    }
                                } catch (e: Exception) {
                                    Log.e("PraisePrison", "📍 주소 변환 실패", e)
                                }
                                
                                // 격자 좌표로 변환
                                val (convertedNx, convertedNy) = LocationConverter.convertToGrid(location.latitude, location.longitude)
                                Log.d("PraisePrison", "📍 변환된 격자 좌표: nx=$convertedNx, ny=$convertedNy")
                                
                                // 날씨 정보 요청
                                lifecycleScope.launch {
                                    getWeatherInfo(baseDate, baseTime, convertedNx, convertedNy)
                                }
                            } else {
                                Log.e("PraisePrison", "📍 위치 정보를 가져올 수 없습니다")
                                showLocationError()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("PraisePrison", "📍 위치 정보 가져오기 실패", e)
                            showLocationError()
                        }
                } catch (e: SecurityException) {
                    Log.e("PraisePrison", "📍 위치 권한 오류", e)
                    showLocationError()
                }
            }
            else -> {
                Log.d("PraisePrison", "📍 위치 권한 없음")
                requestLocationPermission()
            }
        }
    }

    private suspend fun getWeatherInfo(baseDate: String, baseTime: String, nx: Int, ny: Int) {
        try {
            Log.d("PraisePrison", "\uD83C\uDF24 날씨 정보 요청: baseDate=$baseDate, baseTime=$baseTime, nx=$nx, ny=$ny")
            
            val response = WeatherApiClient.weatherApi.getWeather(
                serviceKey = getString(R.string.weather_api_key),
                baseDate = baseDate,
                baseTime = baseTime,
                nx = nx,
                ny = ny
            )

            // API 에러 코드 체크
            if (response.response.header.resultCode != "00") {
                Log.e("PraisePrison", "❌ API 에러: ${response.response.header.resultMsg} (${response.response.header.resultCode})")
                activity?.runOnUiThread {
                    updateWeatherDisplay(WeatherInfo(
                        sky = "맑음",
                        temperature = "0°C",
                        humidity = "",
                        rain = "",
                        wind = "",
                        weatherCode = "1"
                    ))
                }
                return
            }

            val items = response.response.body?.items?.item ?: run {
                Log.e("PraisePrison", "❌ 날씨 정보 응답이 비어있습니다")
                activity?.runOnUiThread {
                    updateWeatherDisplay(WeatherInfo(
                        sky = "맑음",
                        temperature = "0°C",
                        humidity = "",
                        rain = "",
                        wind = "",
                        weatherCode = "1"
                    ))
                }
                return
            }

            // WeatherData를 사용하여 날씨 정보 파싱
            val weatherData = WeatherData()
            val weatherInfo = weatherData.parseWeatherData(response)
            
            activity?.runOnUiThread {
                updateWeatherDisplay(weatherInfo)
            }
        } catch (e: Exception) {
            Log.e("PraisePrison", "❌ 날씨 정보 가져오기 실패", e)
            activity?.runOnUiThread {
                updateWeatherDisplay(WeatherInfo(
                    sky = "맑음",
                    temperature = "0°C",
                    humidity = "",
                    rain = "",
                    wind = "",
                    weatherCode = "1"
                ))
            }
        }
    }

    private fun showLocationError() {
        Toast.makeText(requireContext(), "위치 정보를 가져올 수 없습니다. GPS 설정을 확인해주세요.", Toast.LENGTH_LONG).show()
        // 기본값으로 부산 좌표 사용
        val (defaultNx, defaultNy) = LocationConverter.convertToGrid(35.1631, 129.1637)
        lifecycleScope.launch {
            getWeatherInfo(
                SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Calendar.getInstance().time),
                "0200",
                defaultNx,
                defaultNy
            )
        }
    }

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private var currentWeatherType: Int = 1  // 기본값: 맑음

    private fun updateWeatherDisplay(weatherInfo: WeatherInfo) {
        currentWeatherInfo = weatherInfo
        currentWeatherType = weatherInfo.weatherCode.toIntOrNull() ?: 1

        // 날씨 아이콘 설정
        val iconResId = when (currentWeatherType) {
            1 -> R.drawable.ic_weather_sunny
            2 -> R.drawable.ic_weather_cloudy
            3 -> R.drawable.ic_weather_rain
            4 -> R.drawable.ic_weather_snow
            5 -> R.drawable.ic_weather_thunderstorm
            else -> R.drawable.ic_weather_sunny
        }
        binding.weatherIcon.setImageResource(iconResId)
        binding.weatherIcon.visibility = View.VISIBLE
    }

    private fun setupMoodSelection() {
        binding.moodChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds[0])
                selectedMood = chip.text.toString()
            }
        }
    }

    private fun setupImageButtons() {
        binding.imagePickerCard.setOnClickListener {
            showImagePickerDialog()
        }
    }

    private fun showImagePickerDialog() {
        val items = arrayOf("갤러리에서 선택", "사진 촬영")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("사진 첨부")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> checkGalleryPermission()
                    1 -> checkCameraPermission()
                }
            }
            .show()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun checkGalleryPermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                openGallery()
            }
            else -> {
                requestGalleryPermission.launch(permission)
            }
        }
    }

    private fun openCamera() {
        try {
            val photoFile = File.createTempFile(
                "photo_${System.currentTimeMillis()}", 
                ".jpg", 
                requireContext().cacheDir
            )
            
            tempPhotoUri = FileProvider.getUriForFile(
                requireContext(),
                "com.example.praiseprisonapp.fileprovider",
                photoFile
            )

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, tempPhotoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            // 카메라 앱이 있는지 확인
            intent.resolveActivity(requireContext().packageManager)?.let {
                cameraLauncher.launch(intent)
            } ?: run {
                Toast.makeText(context, "카메라 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("DiaryWriteFragment", "카메라 실행 중 오류 발생", e)
            Toast.makeText(context, "카메라를 실행할 수 없습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun showImagePreview(uri: Uri) {
        binding.imagePickerOverlay.visibility = View.GONE
        Glide.with(this)
            .load(uri)
            .into(binding.ivPreview)
    }

    private fun setupContentField() {
        binding.etContent.setOnFocusChangeListener { _, hasFocus ->
            binding.contentLayout.hint = if (hasFocus) "" else "나의 모습을 칭찬해보세요"
        }
    }

    private fun setupSendButton() {
        binding.sendButton.setOnClickListener {
            Log.d(TAG, "Send button clicked")
            val content = binding.etContent.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(context, "내용을 입력해주세요", Toast.LENGTH_SHORT).show()
                binding.etContent.error = "내용을 입력해주세요"
                return@setOnClickListener
            }

            if (selectedMood == null) {
                Toast.makeText(context, "오늘의 감정을 선택해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 감정 분석 수행
            Log.d(TAG, "Starting sentiment analysis")
            analyzeDiaryContent(content) {
                Log.d(TAG, "Proceeding to save diary")
                saveDiary()
            }
        }
    }

    private fun analyzeDiaryContent(content: String, onSuccess: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d(TAG, "일기 내용 감정 분석 시작")
            val result = withContext(Dispatchers.Default) {
                sentimentAnalyzer.analyze(content)
            }
            
            when {
                // 신뢰도가 매우 낮은 경우 (10% 미만)
                result.confidence < 0.1 -> {
                    Log.d(TAG, "감정 분석 신뢰도가 너무 낮음 (${String.format("%.1f", result.confidence * 100)}%) - 재확인 요청")
                    showSentimentConfirmationDialog(result.emotion, content)
                }
                // 기쁨 그룹이 아닌 모든 감정은 부정적으로 처리
                result.emotionGroup != SentimentAnalyzer.EmotionGroup.JOY -> {
                    Log.d(TAG, "부정적 감정 감지됨 - 확인 다이얼로그 표시")
                    showSentimentConfirmationDialog(result.emotion, content)
                }
                else -> {
                    Log.d(TAG, "긍정적 감정 감지됨 - 일기 저장 진행")
                    onSuccess()
                }
            }
        }
    }

    private fun showSentimentConfirmationDialog(title: String, content: String) {
        if (!isAdded) {
            Log.w(TAG, "Fragment가 아직 추가되지 않음. 다이얼로그 표시 스킵")
            return
        }

        val message = "부정적인 감정이 감지되었어요.\n긍정적인 말로 수정하는건 어떨까요?"
        
        activity?.let { activity ->
            AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton("네") { _, _ ->
                    Log.d(TAG, "사용자가 텍스트 수정하기로 선택")
                    // 작성 화면에 그대로 남아있게 됨 (수정 가능)
                }
                .setNegativeButton("아니오") { _, _ ->
                    Log.d(TAG, "사용자가 수정하지 않고 게시하기로 선택")
                    saveDiary()
                }
                .show()
        } ?: run {
            Log.w(TAG, "Activity가 null임. 다이얼로그 표시 스킵하고 바로 저장")
            saveDiary()
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.isVisible = isLoading
        binding.sendButton.isEnabled = !isLoading
    }

    private fun saveDiary() {
        val content = binding.etContent.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(context, "내용을 입력해주세요", Toast.LENGTH_SHORT).show()
            binding.etContent.error = "내용을 입력해주세요"
            return
        }

        if (selectedMood == null) {
            Toast.makeText(context, "오늘의 감정을 선택해주세요", Toast.LENGTH_SHORT).show()
            return
        }
        showLoading(true)

        // 버튼 비활성화
        binding.sendButton.isEnabled = false

        // 이미지가 있으면 먼저 업로드
        if (selectedImageUri != null) {
            uploadImageAndSaveDiary(content)
        } else {
            saveDiaryToFirestore(content, "")
        }
    }

    private fun uploadImageAndSaveDiary(content: String) {
        val imageRef = storage.reference.child("diary_images/${System.currentTimeMillis()}.jpg")

        selectedImageUri?.let { uri ->
            imageRef.putFile(uri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        saveDiaryToFirestore(content, downloadUrl.toString())
                    }
                }
                .addOnFailureListener {
                    Log.d("FirebaseStorage", "Storage ref: ${imageRef.path} | ${imageRef.bucket}")
                    Toast.makeText(context, "이미지 업로드 실패", Toast.LENGTH_SHORT).show()
                    binding.sendButton.isEnabled = true
                    showLoading(false)
                }
        }
    }

    private fun saveDiaryToFirestore(content: String, imageUrl: String) {
        val currentUser = auth.currentUser ?: return
        val diaryRef = db.collection("diaries").document()

        val diary = DiaryData(
            id = diaryRef.id,
            groupId = groupId,
            authorId = currentUser.uid,
            authorName = currentUser.displayName ?: "익명",
            content = content,
            imageUrl = imageUrl,
            mood = selectedMood ?: "",
            createdAt = Timestamp.now(),
            weatherType = currentWeatherType,
            commentCount = 0,
            reactions = mapOf("stability" to 0)
        )

        diaryRef.set(diary)
            .addOnSuccessListener {
                Toast.makeText(context, "일기가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                showLoading(false)
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener { e ->
                Log.e("PraisePrison", "일기 저장 실패", e)
                Toast.makeText(context, "일기 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                binding.sendButton.isEnabled = true
                showLoading(false)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopLocationUpdates()
        _binding = null
    }

    companion object {
        private const val TAG = "DiaryWriteFragment"
        
        fun newInstance(groupId: String) = DiaryWriteFragment().apply {
            arguments = Bundle().apply {
                putString("group_id", groupId)
            }
        }

        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }
}