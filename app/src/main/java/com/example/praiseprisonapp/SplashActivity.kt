package com.example.praiseprisonapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()

        // 코루틴 사용해서 2초 대기 후 로그인 상태 확인
        lifecycleScope.launch {
            delay(2000)

            val nextActivity = if (auth.currentUser != null) {
                MainActivity::class.java
            } else {
                LoginActivity::class.java
            }

            startActivity(Intent(this@SplashActivity, nextActivity))
            finish()
        }
    }
}
