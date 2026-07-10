package com.velaud.chat.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.velaud.chat.databinding.ActivityVerifyBinding
import com.velaud.chat.network.ApiClient
import com.velaud.chat.ui.main.MainActivity
import kotlinx.coroutines.launch

class VerifyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerifyBinding
    private lateinit var auth: FirebaseAuth
    private var email: String = ""
    private val codeInputs = mutableListOf<EditText>()
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        email = intent.getStringExtra("email") ?: ""

        binding.tvEmailAddress.text = email
        setupCodeInputs()
        setupClickListeners()
        startCountdown()
    }

    private fun setupCodeInputs() {
        codeInputs.addAll(listOf(
            binding.etCode1, binding.etCode2, binding.etCode3,
            binding.etCode4, binding.etCode5, binding.etCode6
        ))

        codeInputs.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val value = s?.toString() ?: ""
                    if (value.isNotEmpty()) {
                        // Move to next input
                        if (index < codeInputs.size - 1) {
                            codeInputs[index + 1].requestFocus()
                        }
                    }
                    checkCodeComplete()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            editText.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (editText.text.isEmpty() && index > 0) {
                        codeInputs[index - 1].requestFocus()
                        codeInputs[index - 1].text.clear()
                    }
                }
                false
            }
        }

        // Focus first input
        codeInputs[0].requestFocus()
    }

    private fun checkCodeComplete() {
        val code = getCode()
        val isComplete = code.length == 6
        binding.btnVerify.isEnabled = isComplete
        binding.btnVerify.alpha = if (isComplete) 1f else 0.5f
    }

    private fun getCode(): String {
        return codeInputs.joinToString("") { it.text.toString() }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnVerify.setOnClickListener {
            val code = getCode()
            if (code.length == 6) {
                verifyCode(code)
            }
        }

        binding.btnResend.setOnClickListener {
            if (!binding.btnResend.isEnabled) return@setOnClickListener
            resendCode()
        }
    }

    private fun verifyCode(code: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = ApiClient.authService.verifyCode(
                    mapOf("email" to email, "code" to code)
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    val customToken = body?.get("customToken") as? String
                    if (!customToken.isNullOrEmpty()) {
                        signInWithCustomToken(customToken)
                    } else {
                        setLoading(false)
                        Toast.makeText(this@VerifyActivity, "Sunucu hatası. Tekrar deneyin.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    setLoading(false)
                    Toast.makeText(this@VerifyActivity, "Kod hatalı. Tekrar deneyin.", Toast.LENGTH_SHORT).show()
                    codeInputs.forEach { it.text.clear() }
                    codeInputs[0].requestFocus()
                }
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@VerifyActivity, "Bağlantı hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun signInWithCustomToken(token: String) {
        auth.signInWithCustomToken(token)
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finishAffinity()
                } else {
                    Toast.makeText(this, "Giriş başarısız: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun resendCode() {
        lifecycleScope.launch {
            try {
                ApiClient.authService.sendVerificationCode(mapOf("email" to email))
                Toast.makeText(this@VerifyActivity, "Kod tekrar gönderildi", Toast.LENGTH_SHORT).show()
                startCountdown()
            } catch (e: Exception) {
                Toast.makeText(this@VerifyActivity, "Gönderme hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCountdown() {
        binding.btnResend.isEnabled = false
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.btnResend.text = "Tekrar gönder ($seconds)"
            }
            override fun onFinish() {
                binding.btnResend.isEnabled = true
                binding.btnResend.text = "Tekrar gönder"
            }
        }.start()
    }

    private fun setLoading(loading: Boolean) {
        binding.btnVerify.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
