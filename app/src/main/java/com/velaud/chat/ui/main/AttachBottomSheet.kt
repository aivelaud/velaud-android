package com.velaud.chat.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.velaud.chat.databinding.BottomSheetAttachBinding

class AttachBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAttachBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(): AttachBottomSheet = AttachBottomSheet()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAttachBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowCamera.setOnClickListener {
            // TODO: Open camera
            dismissAllowingStateLoss()
        }

        binding.rowPhotos.setOnClickListener {
            // TODO: Open gallery
            dismissAllowingStateLoss()
        }

        binding.rowFiles.setOnClickListener {
            // TODO: Open file picker
            dismissAllowingStateLoss()
        }

        binding.switchWebSearch.setOnClickListener {
            val on = binding.switchWebSearch.tag != "on"
            binding.switchWebSearch.tag = if (on) "on" else null
            binding.switchWebSearch.isActivated = on
        }

        binding.switchThinking.isActivated = true
        binding.switchThinking.tag = "on"
        binding.switchThinking.setOnClickListener {
            val on = binding.switchThinking.tag != "on"
            binding.switchThinking.tag = if (on) "on" else null
            binding.switchThinking.isActivated = on
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
