package com.velaud.chat.ui.main

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.velaud.chat.databinding.FragmentDrawerBinding
import com.velaud.chat.network.ApiClient
import kotlinx.coroutines.launch

class DrawerFragment : Fragment() {

    private var _binding: FragmentDrawerBinding? = null
    private val binding get() = _binding!!
    private lateinit var chatAdapter: ChatHistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        setupProfile()
        setupSearch()
        setupClickListeners()
        loadChatHistory()
    }

    private fun setupAdapter() {
        chatAdapter = ChatHistoryAdapter { chatId, title ->
            (activity as? MainActivity)?.openChat(chatId, title)
        }
        binding.rvChats.adapter = chatAdapter
    }

    private fun setupProfile() {
        val user = FirebaseAuth.getInstance().currentUser
        val initial = (user?.displayName ?: user?.email ?: "U")
            .firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        binding.tvProfileInitial.text = initial
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                chatAdapter.filter(s.toString())
            }
        })
    }

    private fun setupClickListeners() {
        binding.navChats.setOnClickListener { /* already showing chats */ }
        binding.navProjects.setOnClickListener { /* TODO: Projects */ }
        binding.navFiles.setOnClickListener { /* TODO: Files */ }

        binding.btnNewChat.setOnClickListener {
            (activity as? MainActivity)?.let { main ->
                main.drawerLayout?.closeDrawer(GravityCompat.START)
            }
        }

        binding.tvProfileInitial.setOnClickListener {
            (activity as? MainActivity)?.signOut()
        }
    }

    private fun loadChatHistory() {
        lifecycleScope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                val token = user?.getIdToken(false)?.result?.token ?: return@launch
                val response = ApiClient.chatService.getChatHistory("Bearer $token")
                if (response.isSuccessful) {
                    val chats = response.body() ?: emptyList()
                    chatAdapter.submitList(chats)
                }
            } catch (e: Exception) {
                // Silent fail - empty history shown
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
