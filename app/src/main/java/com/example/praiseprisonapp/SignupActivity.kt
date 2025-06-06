package com.example.praiseprisonapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.praiseprisonapp.databinding.ActivitySignupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val TAG = "SignupActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupListeners()
    }

    private fun setupListeners() {
        binding.signupButton.setOnClickListener {
            val email = binding.emailEdit.text.toString().trim()
            val password = binding.passwordEdit.text.toString()
            val nickname = binding.nicknameEdit.text.toString().trim()

            // 입력값 유효성 검사
            if (!validateInputs(email, password, nickname)) {
                return@setOnClickListener
            }

            // 회원가입 버튼 비활성화
            binding.signupButton.isEnabled = false

            // Firebase Authentication으로 사용자 생성
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    // 회원가입 버튼 다시 활성화
                    binding.signupButton.isEnabled = true

                    if (task.isSuccessful) {
                        // 사용자 정보를 Firestore에 저장
                        val user = auth.currentUser
                        val userData = hashMapOf(
                            "email" to email,
                            "nickname" to nickname,
                            "createdAt" to com.google.firebase.Timestamp.now()
                        )

                        db.collection("users")
                            .document(user?.uid ?: "")
                            .set(userData)
                            .addOnSuccessListener {
                                // 회원가입 성공 후 로그아웃
                                auth.signOut()
                                Toast.makeText(this,
                                    "회원가입이 완료되었습니다.\n로그인해주세요.",
                                    Toast.LENGTH_SHORT).show()
                                finish() // 로그인 화면으로 돌아가기
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "사용자 정보 저장 실패", e)
                                Toast.makeText(this,
                                    "사용자 정보 저장에 실패했습니다. 다시 시도해주세요.",
                                    Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        val exception = task.exception
                        Log.e(TAG, "회원가입 실패", exception)

                        val errorMessage = when (exception) {
                            is FirebaseAuthWeakPasswordException -> "비밀번호는 최소 6자 이상이어야 합니다."
                            is FirebaseAuthInvalidCredentialsException -> "이메일 형식이 올바르지 않습니다."
                            is FirebaseAuthUserCollisionException -> "이미 사용 중인 이메일입니다."
                            else -> "회원가입에 실패했습니다. 다시 시도해주세요."
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun validateInputs(email: String, password: String, nickname: String): Boolean {
        if (email.isEmpty() || password.isEmpty() || nickname.isEmpty()) {
            Toast.makeText(this, "모든 필드를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "올바른 이메일 주소를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.length < 6) {
            Toast.makeText(this, "비밀번호는 최소 6자 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (nickname.length < 2 || nickname.length > 20) {
            Toast.makeText(this, "닉네임은 2자 이상 20자 이하여야 합니다.", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }
}