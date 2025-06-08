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

class GroupEditFragment : Fragment() {
    private var _binding: GroupEditBinding? = null
    private val binding get() = _binding!!
    private var selectedImageUri: Uri? = null
    private var groupId: String? = null
    
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            binding.groupImage.setImageURI(it)
            binding.imagePickerOverlay.visibility = View.GONE
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
        
        setupUI()
        loadGroupData()
        setupListeners()
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
                        val imageUrl = data["imageUrl"] as? String
                        if (!imageUrl.isNullOrEmpty()) {
                            Glide.with(requireContext())
                                .load(imageUrl)
                                .into(binding.groupImage)
                            binding.imagePickerOverlay.visibility = View.GONE
                        }
                    }
                }
        }
    }

    private fun setupListeners() {
        binding.imagePickerCard.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.updateButton.setOnClickListener {
            updateGroupInfo()
        }
    }

    private fun updateGroupInfo() {
        if (selectedImageUri == null) {
            // 이미지가 선택되지 않은 경우 바로 Firestore 업데이트
            updateFirestore(getUpdates())
            return
        }

        try {
            // 이미지 압축
            val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, selectedImageUri)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val imageData = baos.toByteArray()

            // Storage 참조 생성 및 업로드
            val filename = "group_images/${System.currentTimeMillis()}.jpg"
            val imageRef = FirebaseStorage.getInstance().reference.child(filename)
            
            // 업로드 버튼 비활성화
            binding.updateButton.isEnabled = false
            
            imageRef.putBytes(imageData)
                .addOnSuccessListener {
                    imageRef.downloadUrl
                        .addOnSuccessListener { uri ->
                            val updates = getUpdates()
                            updates["imageUrl"] = uri.toString()
                            updateFirestore(updates)
                        }
                        .addOnFailureListener { e ->
                            handleError("이미지 URL 가져오기 실패: ${e.message}", e)
                            binding.updateButton.isEnabled = true
                        }
                }
                .addOnFailureListener { e ->
                    handleError("이미지 업로드 실패: ${e.message}", e)
                    binding.updateButton.isEnabled = true
                }
        } catch (e: Exception) {
            handleError("이미지 처리 실패: ${e.message}", e)
            binding.updateButton.isEnabled = true
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
                    parentFragmentManager.popBackStack()
                }
                .addOnFailureListener {
                    binding.updateButton.isEnabled = true
                    Toast.makeText(context, "그룹 정보 수정 실패", Toast.LENGTH_SHORT).show()
                }
        }
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