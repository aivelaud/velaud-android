package com.velaud.chat.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.velaud.chat.R
import com.velaud.chat.databinding.ActivityLoginBinding
import com.velaud.chat.network.ApiClient
import com.velaud.chat.ui.main.MainActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        setupGoogleSignIn()
        setupEmailInput()
        setupClickListeners()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupEmailInput() {
        binding.etEmail.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val isValid = Patterns.EMAIL_ADDRESS.matcher(s ?: "").matches()
                binding.btnEmailContinue.isEnabled = isValid
                binding.btnEmailContinue.alpha = if (isValid) 1f else 0.55f
            }
        })
    }

    private fun setupClickListeners() {
        binding.btnGoogleContinue.setOnClickListener {
            startGoogleSignIn()
        }

        binding.btnEmailContinue.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                sendVerificationCode(email)
            } else {
                Toast.makeText(this, "Geçerli bir e-posta adresi girin", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startGoogleSignIn() {
        setLoading(true)
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun sendVerificationCode(email: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = ApiClient.authService.sendVerificationCode(
                    mapOf("email" to email)
                )
                if (response.isSuccessful) {
                    setLoading(false)
                    val intent = Intent(this@LoginActivity, VerifyActivity::class.java)
                    intent.putExtra("email", email)
                    startActivity(intent)
                } else {
                    setLoading(false)
                    Toast.makeText(this@LoginActivity, "Kod gönderilemedi. Tekrar deneyin.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                setLoading(false)
                Log.e(TAG, "Send code error", e)
                Toast.makeText(this@LoginActivity, "Bağlantı hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                setLoading(false)
                Log.w(TAG, "Google sign in failed", e)
                Toast.makeText(this, "Google girişi başarısız: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    navigateToMain()
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Giriş başarısız: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.btnEmailContinue.isEnabled = !loading
        binding.btnGoogleContinue.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
