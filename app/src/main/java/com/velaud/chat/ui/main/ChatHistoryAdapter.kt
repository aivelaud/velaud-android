package com.velaud.chat.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.velaud.chat.databinding.ItemChatHistoryBinding
import com.velaud.chat.network.ChatHistoryItem

class ChatHistoryAdapter(
    private val onClick: (chatId: String, title: String) -> Unit
) : ListAdapter<ChatHistoryItem, ChatHistoryAdapter.VH>(DIFF) {

    private var fullList = listOf<ChatHistoryItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChatHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    fun filter(query: String) {
        if (query.isBlank()) {
            submitList(fullList)
        } else {
            submitList(fullList.filter { it.title.contains(query, ignoreCase = true) })
        }
    }

    override fun submitList(list: List<ChatHistoryItem>?) {
        if (list != null) fullList = list
        super.submitList(list)
    }

    inner class VH(private val binding: ItemChatHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatHistoryItem) {
            binding.tvChatTitle.text = item.title
            binding.root.setOnClickListener { onClick(item.id, item.title) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ChatHistoryItem>() {
            override fun areItemsTheSame(a: ChatHistoryItem, b: ChatHistoryItem) = a.id == b.id
            override fun areContentsTheSame(a: ChatHistoryItem, b: ChatHistoryItem) = a == b
        }
    }
}
