package com.velaud.chat.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.velaud.chat.databinding.BottomSheetModelBinding
import com.velaud.chat.databinding.ItemModelBinding

class ModelBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetModelBinding? = null
    private val binding get() = _binding!!
    // Callback now provides (modelId, displayName)
    private var onModelSelected: ((String, String) -> Unit)? = null

    data class ModelItem(
        val id: String,         // backend model key
        val name: String,       // display name
        val description: String
    )

    private val models = listOf(
        ModelItem("claude-opus-4-7",   "Claude Opus 4.7",  "En güçlü model. Karmaşık akıl yürütme ve derin analizde lider."),
        ModelItem("claude-sonnet-4-6", "Claude Sonnet 4.6","Hız ve zekanın dengesi. Günlük görevler için verimli."),
        ModelItem("claude-opus-4-6",   "Claude Opus 4.6",  "Uzun içerik üretimi ve akademik yazımda güçlü."),
        ModelItem("gpt-5-4",           "GPT 5.4",          "Genel amaçlı asistan. Yaratıcı yazım ve sohbette çok yönlü."),
        ModelItem("gpt-5-4-pro",       "GPT 5.4 Pro",      "Gelişmiş araştırma ve profesyonel görevler için optimize."),
        ModelItem("kimi-k-2-6",        "Kimi k-2.6",       "Çok uzun bağlam penceresi ve güçlü çok dilli destek."),
        ModelItem("deepseek-v4",       "DeepSeek V4",      "Matematik, mantık ve algoritma problemlerinde uzman.")
    )

    private var selectedModelId = "claude-opus-4-7"

    companion object {
        fun newInstance(onSelected: (String, String) -> Unit): ModelBottomSheet {
            return ModelBottomSheet().apply {
                onModelSelected = onSelected
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetModelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = ModelAdapter()
        binding.rvModels.layoutManager = LinearLayoutManager(requireContext())
        binding.rvModels.adapter = adapter
    }

    inner class ModelAdapter : RecyclerView.Adapter<ModelAdapter.VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemModelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }
        override fun getItemCount() = models.size
        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(models[position])

        inner class VH(private val b: ItemModelBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: ModelItem) {
                b.tvModelName.text = item.name
                b.tvModelDesc.text = item.description
                b.ivCheck.visibility = if (item.id == selectedModelId) View.VISIBLE else View.INVISIBLE
                b.root.isActivated = item.id == selectedModelId
                b.root.setOnClickListener {
                    selectedModelId = item.id
                    onModelSelected?.invoke(item.id, item.name)
                    notifyDataSetChanged()
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
