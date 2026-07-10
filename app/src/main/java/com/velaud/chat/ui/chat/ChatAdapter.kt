package com.velaud.chat.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.velaud.chat.R
import com.velaud.chat.databinding.ItemMessageAiBinding
import com.velaud.chat.databinding.ItemMessageErrorBinding
import com.velaud.chat.databinding.ItemMessageThinkingBinding
import com.velaud.chat.databinding.ItemMessageUserBinding
import com.velaud.chat.model.ChatMessage
import com.velaud.chat.model.MessageRole
import io.noties.markwon.Markwon

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private val expandedThinking = mutableSetOf<Int>()

    companion object {
        const val TYPE_USER = 0
        const val TYPE_THINKING = 1
        const val TYPE_AI = 2
        const val TYPE_ERROR = 3
    }

    fun submitMessages(list: List<ChatMessage>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    fun updateItem(position: Int, item: ChatMessage) {
        if (position >= 0 && position < messages.size) {
            messages[position] = item
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int) = when (messages[position].role) {
        MessageRole.USER -> TYPE_USER
        MessageRole.THINKING -> TYPE_THINKING
        MessageRole.ASSISTANT -> TYPE_AI
        MessageRole.ERROR -> TYPE_ERROR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserVH(ItemMessageUserBinding.inflate(inflater, parent, false))
            TYPE_THINKING -> ThinkingVH(ItemMessageThinkingBinding.inflate(inflater, parent, false))
            TYPE_AI -> AiVH(ItemMessageAiBinding.inflate(inflater, parent, false))
            else -> ErrorVH(ItemMessageErrorBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is UserVH -> holder.bind(msg)
            is ThinkingVH -> holder.bind(msg)
            is AiVH -> holder.bind(msg, position)
            is ErrorVH -> holder.bind(msg)
        }
    }

    inner class UserVH(private val b: ItemMessageUserBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: ChatMessage) {
            b.tvMessage.text = msg.content
        }
    }

    inner class ThinkingVH(private val b: ItemMessageThinkingBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: ChatMessage) {
            val sec = msg.thinkingSeconds
            b.tvThinkingLabel.text = "Thinking"
            b.tvThinkingTime.text = if (sec > 0) "${sec}s" else ""

            // Pulse dots animation
            val anim = AnimationUtils.loadAnimation(b.root.context, R.anim.dot_pulse)
            b.dot1.startAnimation(anim)
            b.dot2.startAnimation(AnimationUtils.loadAnimation(b.root.context, R.anim.dot_pulse_2))
            b.dot3.startAnimation(AnimationUtils.loadAnimation(b.root.context, R.anim.dot_pulse_3))
        }
    }

    inner class AiVH(private val b: ItemMessageAiBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: ChatMessage, position: Int) {
            val markwon = Markwon.create(b.root.context)
            markwon.setMarkdown(b.tvAnswer, msg.content)

            // Thinking section
            val hasThinking = !msg.thinkingText.isNullOrBlank()
            if (hasThinking) {
                b.layoutThinkingHead.visibility = View.VISIBLE
                b.tvThinkingLabel.text = "Thinking"
                b.tvThinkingTime.text = if ((msg.thinkingSeconds ?: 0) > 0) "${msg.thinkingSeconds}s" else ""

                val isExpanded = expandedThinking.contains(position)
                b.ivThinkingChevron.rotation = if (isExpanded) 180f else 0f
                b.tvThinkingBody.visibility = if (isExpanded) View.VISIBLE else View.GONE
                b.tvThinkingBody.text = msg.thinkingText

                b.btnThinkingToggle.setOnClickListener {
                    val expanded = expandedThinking.contains(position)
                    if (expanded) {
                        expandedThinking.remove(position)
                        b.ivThinkingChevron.animate().rotation(0f).setDuration(250).start()
                        b.tvThinkingBody.visibility = View.GONE
                    } else {
                        expandedThinking.add(position)
                        b.ivThinkingChevron.animate().rotation(180f).setDuration(250).start()
                        b.tvThinkingBody.visibility = View.VISIBLE
                    }
                }
            } else {
                b.layoutThinkingHead.visibility = View.GONE
                b.tvThinkingBody.visibility = View.GONE
            }

            // Action buttons
            b.btnCopy.setOnClickListener {
                val clipboard = b.root.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Velaud", msg.content))
                b.btnCopy.setImageResource(R.drawable.ic_check)
                b.btnCopy.postDelayed({ b.btnCopy.setImageResource(R.drawable.ic_copy) }, 1400)
            }

            b.btnThumbUp.setOnClickListener {
                val active = b.btnThumbUp.tag == "active"
                b.btnThumbUp.tag = if (active) null else "active"
                b.btnThumbUp.setColorFilter(
                    if (!active) b.root.context.getColor(R.color.text_primary)
                    else b.root.context.getColor(R.color.muted)
                )
            }

            b.btnThumbDown.setOnClickListener {
                val active = b.btnThumbDown.tag == "active"
                b.btnThumbDown.tag = if (active) null else "active"
                b.btnThumbDown.setColorFilter(
                    if (!active) b.root.context.getColor(R.color.text_primary)
                    else b.root.context.getColor(R.color.muted)
                )
            }

            b.btnRegenerate.setOnClickListener {
                b.btnRegenerate.animate().rotationBy(360f).setDuration(500).start()
            }

            b.btnShare.setOnClickListener {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(android.content.Intent.EXTRA_TEXT, msg.content)
                b.root.context.startActivity(android.content.Intent.createChooser(intent, "Paylaş"))
            }
        }
    }

    inner class ErrorVH(private val b: ItemMessageErrorBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: ChatMessage) {
            b.tvError.text = msg.content
        }
    }
}
