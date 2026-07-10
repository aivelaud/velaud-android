package com.velaud.chat.ui.main

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.google.firebase.auth.FirebaseAuth
import com.velaud.chat.databinding.ActivityMainBinding
import com.velaud.chat.ui.auth.LoginActivity
import com.velaud.chat.ui.chat.ChatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private var currentChatId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        setupUI()
        setupDrawer()
        startLogoAnimation()
    }

    private fun setupUI() {
        val user = auth.currentUser
        val displayName = user?.displayName ?: user?.email?.substringBefore("@") ?: "Kullanıcı"
        binding.tvWelcome.text = "Welcome $displayName"

        binding.btnHamburger.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.btnNewChat.setOnClickListener {
            startNewChat()
        }

        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        binding.btnPlus.setOnClickListener {
            showAttachSheet()
        }

        binding.btnModelPill.setOnClickListener {
            showModelSheet()
        }

        binding.etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                binding.btnSend.isActivated = hasText
                if (hasText) {
                    binding.btnSend.setBackgroundResource(com.velaud.chat.R.drawable.bg_send_active)
                    binding.btnSend.setColorFilter(android.graphics.Color.BLACK)
                } else {
                    binding.btnSend.setBackgroundResource(com.velaud.chat.R.drawable.bg_send_inactive)
                    binding.btnSend.setColorFilter(android.graphics.Color.parseColor("#6e6e6e"))
                }
            }
        })
    }

    private fun setupDrawer() {
        val drawerFragment = supportFragmentManager.findFragmentById(com.velaud.chat.R.id.fragmentDrawer)
        if (drawerFragment == null) {
            supportFragmentManager.beginTransaction()
                .replace(com.velaud.chat.R.id.fragmentDrawer, DrawerFragment())
                .commit()
        }
    }

    private fun startLogoAnimation() {
        val animator = ObjectAnimator.ofFloat(binding.ivHeroLogo, View.TRANSLATION_Y, 0f, -24f, 0f)
        animator.duration = 4000
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isBlank()) return

        binding.etMessage.text.clear()
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("initial_message", text)
        intent.putExtra("model", binding.tvCurrentModel.text.toString())
        startActivity(intent)
    }

    private fun startNewChat() {
        // Already on home, just clear
    }

    private fun showAttachSheet() {
        val sheet = AttachBottomSheet.newInstance()
        sheet.show(supportFragmentManager, "attach")
    }

    private fun showModelSheet() {
        val sheet = ModelBottomSheet.newInstance { modelName ->
            binding.tvCurrentModel.text = modelName
        }
        sheet.show(supportFragmentManager, "model")
    }

    fun openChat(chatId: String, title: String) {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("chat_id", chatId)
        intent.putExtra("chat_title", title)
        startActivity(intent)
    }

    fun signOut() {
        auth.signOut()
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
