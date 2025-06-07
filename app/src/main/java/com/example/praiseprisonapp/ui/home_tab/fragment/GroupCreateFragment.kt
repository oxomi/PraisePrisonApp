package com.example.praiseprisonapp.ui.home_tab.fragment

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.praiseprisonapp.R
import com.example.praiseprisonapp.data.model.GroupData
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.praiseprisonapp.data.repository.GroupRepository
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class GroupCreateFragment : Fragment(R.layout.group_create) {

    private var currentPhotoPath: String? = null
    private var selectedImageUri: Uri? = null
    private lateinit var groupImage: ImageView
    private lateinit var groupNameInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var groupDescriptionInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var visibilityGroup: android.widget.RadioGroup
    private lateinit var passwordLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var passwordInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var createButton: com.google.android.material.button.MaterialButton
    private val groupRepository = GroupRepository()

    // 카메라 권한 요청 결과 처리
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        }
    }

    // 갤러리 권한 요청 결과 처리
    private val requestGalleryPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openGallery()
        }
    }

    // 갤러리에서 선택한 이미지 결과 처리
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                groupImage.setImageURI(uri)
            }
        }
    }

    // 카메라로 찍은 사진 결과 처리
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoPath?.let { path ->
                selectedImageUri = Uri.fromFile(File(path))
                groupImage.setImageURI(selectedImageUri)
            }
        }
    }

    private suspend fun uploadImage(imageUri: Uri): String {
        return try {
            val storage = FirebaseStorage.getInstance()
            val filename = "group_images/${UUID.randomUUID()}"
            val ref = storage.reference.child(filename)
            
            ref.putFile(imageUri).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            throw e
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        groupImage = view.findViewById(R.id.groupImage)
        groupNameInput = view.findViewById(R.id.groupNameInput)
        groupDescriptionInput = view.findViewById(R.id.groupDescriptionInput)
        visibilityGroup = view.findViewById(R.id.visibilityGroup)
        passwordLayout = view.findViewById(R.id.passwordLayout)
        passwordInput = view.findViewById(R.id.passwordInput)
        createButton = view.findViewById(R.id.createButton)

        visibilityGroup.setOnCheckedChangeListener { _, checkedId ->
            passwordLayout.isVisible = checkedId == R.id.privateGroup
        }

        // 이미지 선택 기능
        view.findViewById<View>(R.id.imagePickerCard).setOnClickListener {
            showImagePickerDialog()
        }

        view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
        }

        // 그룹 생성 버튼 클릭 리스너
        createButton.setOnClickListener {
            handleGroupCreation()
        }
    }

    private fun handleGroupCreation() {
        val groupName = groupNameInput.text.toString().trim()
        val groupDescription = groupDescriptionInput.text.toString().trim()
        val isPublic = visibilityGroup.checkedRadioButtonId == R.id.publicGroup
        val password = if (!isPublic) passwordInput.text.toString().trim() else null

        // 입력 검증
        if (groupName.isEmpty()) {
            Toast.makeText(requireContext(), "그룹명을 입력해주세요", Toast.LENGTH_SHORT).show()
            groupNameInput.error = "그룹명을 입력해주세요"
            return
        }

        if (groupDescription.isEmpty()) {
            Toast.makeText(requireContext(), "그룹 설명을 입력해주세요", Toast.LENGTH_SHORT).show()
            groupDescriptionInput.error = "그룹 설명을 입력해주세요"
            return
        }

        if (!isPublic && password.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "비밀번호를 입력해주세요", Toast.LENGTH_SHORT).show()
            passwordInput.error = "비밀번호를 입력해주세요"
            return
        }

        // 버튼 비활성화
        createButton.isEnabled = false

        // 코루틴으로 그룹 생성
        lifecycleScope.launch {
            try {
                // 이미지 업로드
                val imageUrl = selectedImageUri?.let { uri ->
                    try {
                        uploadImage(uri)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "이미지 업로드에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                        null
                    }
                } ?: ""

                val result = groupRepository.createGroup(
                    name = groupName,
                    description = groupDescription,
                    imageUrl = imageUrl,
                    visibility = if (isPublic) "전체공개" else "일부공개",
                    password = password
                )

                result.onSuccess { newGroup ->
                    // 그룹 생성 성공
                    Toast.makeText(requireContext(), "그룹이 생성되었습니다.", Toast.LENGTH_SHORT).show()
                    
                    // 내 그룹 프래그먼트 찾아서 목록 갱신
                    parentFragmentManager.fragments.forEach { fragment ->
                        when (fragment) {
                            is MyGroupFragment -> {
                                fragment.loadMyGroups() // 전체 목록 새로고침
                            }
                            is AllGroupFragment -> {
                                fragment.loadAllGroups() // 전체 목록 새로고침
                            }
                        }
                    }
                    
                    parentFragmentManager.popBackStack()
                }.onFailure { exception ->
                    // 그룹 생성 실패
                    Toast.makeText(requireContext(), "그룹 생성에 실패했습니다: ${exception.message}", Toast.LENGTH_SHORT).show()
                    createButton.isEnabled = true
                }
            } catch (e: Exception) {
                // 예외 발생
                Toast.makeText(requireContext(), "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                createButton.isEnabled = true
            }
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("카메라로 촬영", "갤러리에서 선택")

        AlertDialog.Builder(requireContext())
            .setTitle("이미지 선택")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermission()
                    1 -> checkGalleryPermission()
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
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { intent ->
            intent.resolveActivity(requireActivity().packageManager)?.also {
                val photoFile = createImageFile()
                photoFile?.also {
                    val photoURI = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        it
                    )
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    takePictureLauncher.launch(intent)
                }
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = requireContext().getExternalFilesDir(null)
            File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            ).apply {
                currentPhotoPath = absolutePath
            }
        } catch (ex: Exception) {
            null
        }
    }
}