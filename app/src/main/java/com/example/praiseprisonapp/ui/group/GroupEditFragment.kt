package com.example.praiseprisonapp.ui.group

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.praiseprisonapp.databinding.GroupEditBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import com.google.firebase.storage.StorageException
import androidx.core.view.isVisible
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class GroupEditFragment : Fragment() {
    private var _binding: GroupEditBinding? = null
    private val binding get() = _binding!!
    private var selectedImageUri: Uri? = null
    private var groupId: String? = null
    private var currentImageUrl: String? = null
    private var isImageChanged = false
    private var tempPhotoUri: Uri? = null
    private lateinit var progressBar: View


    // 갤러리 실행 결과 처리
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                isImageChanged = true
                showImagePreview(uri)
            }
        }
    }

    // 카메라 실행 결과 처리
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            tempPhotoUri?.let { uri ->
                selectedImageUri = uri
                isImageChanged = true
                showImagePreview(uri)
            }
        }
    }

    // 카메라 권한 요청 결과 처리
    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(context, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    // 갤러리 권한 요청 결과 처리
    private val requestGalleryPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            openGallery()
        } else {
            Toast.makeText(context, "갤러리 접근 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getString("groupId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = GroupEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressBar = binding.progressBar

        setupUI()
        loadGroupData()
        setupListeners()
    }
    private fun showLoading(isLoading: Boolean) {
        progressBar.isVisible = isLoading
        binding.updateButton.isEnabled = !isLoading
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.privateGroup.setOnCheckedChangeListener { _, isChecked ->
            binding.passwordLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun loadGroupData() {
        groupId?.let { id ->
            FirebaseFirestore.getInstance().collection("groups")
                .document(id)
                .get()
                .addOnSuccessListener { document ->
                    document.data?.let { data ->
                        binding.groupNameInput.setText(data["name"] as? String ?: "")
                        binding.groupDescriptionInput.setText(data["description"] as? String ?: "")
                        
                        val isPrivate = data["isPrivate"] as? Boolean ?: false
                        if (isPrivate) {
                            binding.privateGroup.isChecked = true
                            binding.passwordLayout.visibility = View.VISIBLE
                            binding.passwordInput.setText(data["password"] as? String ?: "")
                        } else {
                            binding.publicGroup.isChecked = true
                        }

                        // 그룹 이미지 로드
                        currentImageUrl = data["imageUrl"] as? String
                        if (!currentImageUrl.isNullOrEmpty()) {
                            Glide.with(requireContext())
                                .load(currentImageUrl)
                                .into(binding.groupImage)
                            binding.imagePickerOverlay.visibility = View.GONE
                        }
                    }
                }
        }
    }

    private fun setupListeners() {
        binding.imagePickerCard.setOnClickListener {
            showImagePickerDialog()
        }

        binding.updateButton.setOnClickListener {
            if (validateInputs()) {
                updateGroupInfo()
            }
        }
    }

    private fun validateInputs(): Boolean {
        val groupName = binding.groupNameInput.text.toString().trim()
        if (groupName.isEmpty()) {
            binding.groupNameInput.error = "그룹명을 입력해주세요"
            return false
        }

        if (binding.privateGroup.isChecked) {
            val password = binding.passwordInput.text.toString().trim()
            if (password.isEmpty()) {
                binding.passwordInput.error = "비밀번호를 입력해주세요"
                return false
            }
        }

        return true
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
            Log.e("GroupEditFragment", "카메라 실행 중 오류 발생", e)
            Toast.makeText(context, "카메라를 실행할 수 없습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun showImagePreview(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .into(binding.groupImage)
        binding.imagePickerOverlay.visibility = View.GONE
    }

    private fun updateGroupInfo() {
        // 업로드 버튼 비활성화
        binding.updateButton.isEnabled = false
        //로딩 시작
        showLoading(true)
        
        if (!isImageChanged) {
            // 이미지가 변경되지 않은 경우 바로 Firestore 업데이트
            updateFirestore(getUpdates())
            return
        }

        selectedImageUri?.let { uri ->
            try {
                // 이미지 압축 및 리사이징
                val rawBitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                val rotateBitmap = rotateBitmapIfRequired(uri, rawBitmap)
                val resizedBitmap = resizeBitmap(rotateBitmap, 800) // 최대 800px로 리사이징
                val baos = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos) // 압축률 70%로 조정
                val imageData = baos.toByteArray()

                // Storage 참조 생성 및 업로드
                val filename = "group_images/${System.currentTimeMillis()}.jpg"
                val imageRef = FirebaseStorage.getInstance().reference.child(filename)
                
                imageRef.putBytes(imageData)
                    .addOnSuccessListener {
                        imageRef.downloadUrl
                            .addOnSuccessListener { downloadUri ->
                                val updates = getUpdates()
                                updates["imageUrl"] = downloadUri.toString()
                                updateFirestore(updates)
                                
                                // 이전 이미지 삭제
                                deleteOldImage()
                            }
                            .addOnFailureListener { e ->
                                handleError("이미지 URL 가져오기 실패: ${e.message}", e)
                                binding.updateButton.isEnabled = true
                                showLoading(false)
                            }
                    }
                    .addOnFailureListener { e ->
                        handleError("이미지 업로드 실패: ${e.message}", e)
                        binding.updateButton.isEnabled = true
                        showLoading(false)
                    }

                // Bitmap 메모리 해제
                resizedBitmap.recycle()
                rawBitmap.recycle()
                rotateBitmap.recycle()
            } catch (e: Exception) {
                handleError("이미지 처리 실패: ${e.message}", e)
                binding.updateButton.isEnabled = true
                showLoading(false)
            }
        } ?: updateFirestore(getUpdates()) // selectedImageUri가 null인 경우
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        var width = bitmap.width
        var height = bitmap.height
        
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun deleteOldImage() {
        currentImageUrl?.let { url ->
            try {
                val oldImageRef = FirebaseStorage.getInstance().getReferenceFromUrl(url)
                oldImageRef.delete().addOnFailureListener { e ->
                    Log.e("GroupEditFragment", "이전 이미지 삭제 실패", e)
                }
            } catch (e: Exception) {
                Log.e("GroupEditFragment", "이전 이미지 참조 가져오기 실패", e)
            }
        }
    }

    private fun getUpdates(): HashMap<String, Any> {
        return HashMap<String, Any>().apply {
            put("name", binding.groupNameInput.text.toString().trim())
            put("description", binding.groupDescriptionInput.text.toString().trim())
            put("isPrivate", binding.privateGroup.isChecked)
            if (binding.privateGroup.isChecked) {
                put("password", binding.passwordInput.text.toString().trim())
            }
        }
    }

    private fun handleError(message: String, e: Exception) {
        Log.e("GroupEditFragment", message, e)
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun updateFirestore(updates: HashMap<String, Any>) {
        groupId?.let { id ->
            FirebaseFirestore.getInstance().collection("groups")
                .document(id)
                .update(updates)
                .addOnSuccessListener {
                    Toast.makeText(context, "그룹 정보가 수정되었습니다", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                    parentFragmentManager.popBackStack()
                }
                .addOnFailureListener {
                    binding.updateButton.isEnabled = true
                    showLoading(false)
                    Toast.makeText(context, "그룹 정보 수정 실패", Toast.LENGTH_SHORT).show()
                }
        }
    }
    private fun rotateBitmapIfRequired(uri: Uri, bitmap: Bitmap): Bitmap {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val exif = androidx.exifinterface.media.ExifInterface(inputStream!!)
        val orientation = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        )

        val rotatedBitmap = when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }

        inputStream.close()
        return rotatedBitmap
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(groupId: String) = GroupEditFragment().apply {
            arguments = Bundle().apply {
                putString("groupId", groupId)
            }
        }
    }
}