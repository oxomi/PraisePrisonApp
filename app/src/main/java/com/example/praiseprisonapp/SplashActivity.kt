package com.example.praiseprisonapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import android.widget.Toast

// 인트로 화면
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()




        // 2초 후에 로그인 상태 확인 후 적절한 화면으로 이동
        Handler(Looper.getMainLooper()).postDelayed({
            // 현재 로그인된 사용자가 있는지 확인
            if (auth.currentUser != null) {
                // 로그인된 사용자가 있으면 메인 화면으로
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                // 로그인된 사용자가 없으면 로그인 화면으로
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 2000)
    }
} 