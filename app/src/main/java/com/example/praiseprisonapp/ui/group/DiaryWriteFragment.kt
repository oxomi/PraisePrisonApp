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
        setupToolbar()
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

    private fun fetchWeatherInfo(nx: Int, ny: Int) {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HHmm", Locale.getDefault())
        val now = Calendar.getInstance()
        
        if (now.get(Calendar.MINUTE) < 45) {
            now.add(Calendar.HOUR, -1)
        }
        
        val baseDate = dateFormat.format(now.time)
        val baseTime = timeFormat.format(now.time).substring(0, 2) + "00"

        lifecycleScope.launch {
            try {
                val response = WeatherApiClient.weatherApi.getWeather(
                    serviceKey = getString(R.string.weather_api_key),
                    baseDate = baseDate,
                    baseTime = baseTime,
                    nx = nx,
                    ny = ny
                )

                response.response.body?.items?.item?.let { items ->
                    var sky = "1"  // 기본값: 맑음
                    var pty = "0"  // 기본값: 없음
                    var tmp = ""   // 기온

                    for (item in items) {
                        when (item.category) {
                            "SKY" -> sky = item.fcstValue    // 하늘상태
                            "PTY" -> pty = item.fcstValue    // 강수형태
                            "TMP" -> tmp = "${item.fcstValue}°C"  // 기온
                        }
                    }

                    val weatherCode = WeatherApiClient.mapSkyToWeatherCode(sky, pty)
                    val weatherDescription = when (weatherCode) {
                        "1" -> "맑음"
                        "2" -> "흐림"
                        "3" -> "비"
                        "4" -> "눈"
                        "5" -> "천둥번개"
                        else -> "맑음"
                    }
                    Log.d("PraisePrison", "\uD83C\uDF24 현재 날씨: $weatherDescription ($tmp)")

                    val weatherInfo = WeatherInfo(
                        sky = weatherDescription,
                        temperature = tmp,
                        humidity = "",
                        rain = "",
                        wind = "",
                        weatherCode = weatherCode
                    )
                    updateWeatherDisplay(weatherInfo)
                }
            } catch (e: Exception) {
                Log.e("PraisePrison", "❌ 날씨 정보 가져오기 실패")
            }
        }
    }

    private var currentWeatherType: Int = 1  // 기본값: 맑음

    private fun updateWeatherDisplay(weatherInfo: WeatherInfo) {
        // 날씨 코드를 weatherType으로 변환
        currentWeatherType = when (weatherInfo.weatherCode) {
            "1" -> 1  // 맑음
            "2" -> 2  // 흐림
            "3" -> 3  // 비
            "4" -> 4  // 눈
            "5" -> 5  // 천둥번개
            else -> 1 // 기본값: 맑음
        }
        
        // 날씨 아이콘 업데이트
        val iconResId = when (currentWeatherType) {
            1 -> R.drawable.ic_weather_sunny
            2 -> R.drawable.ic_weather_cloudy
            3 -> R.drawable.ic_weather_rain
            4 -> R.drawable.ic_weather_snow
            5 -> R.drawable.ic_weather_thunderstorm
            else -> R.drawable.ic_weather_sunny
        }
        
        binding.weatherIcon.apply {
            setImageResource(iconResId)
            visibility = View.VISIBLE
        }

        // 로그에 현재 날씨 표시
        val weatherName = when (currentWeatherType) {
            1 -> "맑음"
            2 -> "흐림"
            3 -> "비"
            4 -> "눈"
            5 -> "천둥번개"
            else -> "맑음"
        }
        Log.d("PraisePrison", "\uD83C\uDF24 현재 날씨: $weatherName (${weatherInfo.temperature})")
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
        val photoFile = File.createTempFile("photo_", ".jpg", requireContext().cacheDir)
        tempPhotoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            photoFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, tempPhotoUri)
        }
        cameraLauncher.launch(intent)
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
            saveDiary()
        }
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
                    Toast.makeText(context, "이미지 업로드 실패", Toast.LENGTH_SHORT).show()
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
            weatherType = currentWeatherType
        )

        diaryRef.set(diary)
            .addOnSuccessListener {
                Toast.makeText(context, "일기가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener { e ->
                Log.e("PraisePrison", "일기 저장 실패", e)
                Toast.makeText(context, "일기 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                binding.sendButton.isEnabled = true
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopLocationUpdates()
        _binding = null
    }

    companion object {
        fun newInstance(groupId: String) = DiaryWriteFragment().apply {
            arguments = Bundle().apply {
                putString("group_id", groupId)
            }
        }
    }
}