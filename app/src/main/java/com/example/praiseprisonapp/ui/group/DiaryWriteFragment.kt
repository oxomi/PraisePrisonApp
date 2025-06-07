package com.example.praiseprisonapp.ui.group

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.praiseprisonapp.R
import com.example.praiseprisonapp.databinding.DiaryWriteBinding
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiaryWriteFragment : Fragment() {
    private var _binding: DiaryWriteBinding? = null
    private val binding get() = _binding!!
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    
    private var selectedImageUri: Uri? = null
    private var tempPhotoUri: Uri? = null
    private var selectedMood: String? = null
    private lateinit var groupId: String

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
    }

    private fun setupToolbar() {
        // 제목 설정 (닉네임 + 날짜)
        val currentUser = auth.currentUser
        val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        val today = dateFormat.format(Date())

        // 사용자 정보 가져오기
        currentUser?.let { user ->
            // Firestore에서 사용자 정보 가져오기
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val nickname = document.getString("nickname") ?: user.displayName
                        binding.tvUserName.text = nickname
                    } else {
                        // Firestore에 데이터가 없으면 Firebase Auth의 displayName 사용
                        binding.tvUserName.text = user.displayName
                    }
                }
                .addOnFailureListener {
                    // 실패시 Firebase Auth의 displayName 사용
                    binding.tvUserName.text = user.displayName
                }
        }
        
        binding.tvTitle.text = today

        // 뒤로가기
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 저장 버튼 스타일 설정
        binding.toolbar.menu.findItem(R.id.action_save)?.let { menuItem ->
            val actionView = LayoutInflater.from(requireContext()).inflate(R.layout.menu_item_save, null)
            val saveText = actionView.findViewById<TextView>(R.id.saveText)
            saveText.setTextColor(ContextCompat.getColor(requireContext(), R.color.textPrimary))
            saveText.setPadding(0, 0, resources.getDimensionPixelSize(R.dimen.spacing_medium), 0)
            menuItem.actionView = actionView
            actionView.setOnClickListener {
                saveDiary()
            }
        }

        // 저장 버튼
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_save -> {
                    saveDiary()
                    true
                }
                else -> false
            }
        }
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
                    0 -> {
                        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        galleryLauncher.launch(intent)
                    }
                    1 -> {
                        val photoFile = File.createTempFile("photo_", ".jpg", requireContext().cacheDir)
                        tempPhotoUri = FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.provider",
                            photoFile
                        )
                        
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                            putExtra(MediaStore.EXTRA_OUTPUT, tempPhotoUri)
                        }
                        cameraLauncher.launch(intent)
                    }
                }
            }
            .show()
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

    private fun saveDiary() {
        val content = binding.etContent.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(context, "내용을 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedMood == null) {
            Toast.makeText(context, "오늘의 감정을 선택해주세요", Toast.LENGTH_SHORT).show()
            return
        }

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
        
        val diary = hashMapOf(
            "groupId" to groupId,
            "authorId" to currentUser.uid,
            "authorName" to currentUser.displayName,
            "content" to content,
            "imageUrl" to imageUrl,
            "mood" to selectedMood,
            "createdAt" to com.google.firebase.Timestamp.now(),
            "reactions" to mapOf<String, Int>(),
            "commentCount" to 0
        )

        db.collection("diaries")
            .add(diary)
            .addOnSuccessListener {
                Toast.makeText(context, "일기가 저장되었습니다", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener {
                Toast.makeText(context, "저장 실패", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
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