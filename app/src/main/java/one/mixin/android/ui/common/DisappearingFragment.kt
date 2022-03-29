package one.mixin.android.ui.common

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import one.mixin.android.R
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.databinding.FragmentDisappearingBinding
import one.mixin.android.extension.highlightLinkText
import one.mixin.android.extension.withArgs
import one.mixin.android.ui.conversation.ConversationFragment.Companion.CONVERSATION_ID
import one.mixin.android.ui.conversation.ConversationViewModel
import one.mixin.android.util.viewBinding
import one.mixin.android.widget.picker.toTimeInterval
import one.mixin.android.widget.picker.toTimeIntervalIndex
import timber.log.Timber

@AndroidEntryPoint
class DisappearingFragment : BaseFragment(R.layout.fragment_disappearing) {
    companion object {
        const val TAG = "DisappearingFragment"

        fun newInstance(conversationId: String) = DisappearingFragment().withArgs {
            putString(CONVERSATION_ID, conversationId)
        }
    }

    private val conversationId by lazy {
        requireNotNull(requireArguments().getString(CONVERSATION_ID))
    }

    private val viewModel by viewModels<ConversationViewModel>()
    private val binding by viewBinding(FragmentDisappearingBinding::bind)
    private val checkGroup by lazy {
        binding.run {
            listOf(
                disappearingOffIv,
                disappearingOption1Iv,
                disappearingOption2Iv,
                disappearingOption3Iv,
                disappearingOption4Iv,
                disappearingOption5Iv,
                disappearingOption6Iv
            )
        }
    }

    private val pbGroup by lazy {
        binding.run {
            listOf(
                disappearingOffPb,
                disappearingOption1Pb,
                disappearingOption2Pb,
                disappearingOption3Pb,
                disappearingOption4Pb,
                disappearingOption5Pb,
                disappearingOption6Pb
            )
        }
    }

    private fun initOption(interval: Long?) {
        when {
            interval == null || interval <= 0 -> updateOptionCheck(0)
            interval == 30L -> updateOptionCheck(1)
            interval == 600L -> updateOptionCheck(2)
            interval == 7200L -> updateOptionCheck(3)
            interval == 86400L -> updateOptionCheck(4)
            interval == 604800L -> updateOptionCheck(5)
            else -> {
                updateOptionCheck(6)
                binding.disappearingOption6Interval.text = toTimeInterval(interval)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val learn: String = requireContext().getString(R.string.disappearing_content_learn)
        val info = requireContext().getString(R.string.disappearing_content)
        // Todo replace url
        val learnUrl = requireContext().getString(R.string.setting_delete_account_url)
        binding.tipTv.highlightLinkText(info, arrayOf(learn), arrayOf(learnUrl))
        // Todo use real data
        initOption(30)
        binding.apply {
            disappearingOff.setOnClickListener {
                updateUI(0, 0L)
            }
            disappearingOption1.setOnClickListener {
                updateUI(1, 30L)
            }
            disappearingOption2.setOnClickListener {
                updateUI(2, 600L)
            }
            disappearingOption3.setOnClickListener {
                updateUI(3, 7200L)
            }
            disappearingOption4.setOnClickListener {
                updateUI(4, 86400L)
            }
            disappearingOption5.setOnClickListener {
                updateUI(5, 604800L)
            }

            disappearingOption6.setOnClickListener {
                DisappearingIntervalBottomFragment.newInstance(timeInterval)
                    .apply {
                        onSetCallback {
                            this@DisappearingFragment.lifecycleScope.launch {
                                disappearingOption6Iv.isVisible = false
                                disappearingOption6Interval.isVisible = false
                                disappearingOption6Arrow.isVisible = false
                                updateUI(6, it)
                                disappearingOption6Interval.text = toTimeInterval(it)
                                timeInterval = it
                                Timber.e(
                                    "Set interval ${toTimeInterval(it)} ${
                                    toTimeIntervalIndex(
                                        it
                                    )
                                    }"
                                )
                            }
                        }
                    }
                    .showNow(parentFragmentManager, DisappearingIntervalBottomFragment.TAG)
            }
        }
    }

    // Todo replace real data
    private var timeInterval: Long? = null

    private fun updateUI(index: Int, interval: Long) {
        lifecycleScope.launch {
            pbGroup[index].isVisible = true
            handleMixinResponse(
                invokeNetwork = { viewModel.disappear(conversationId, interval) },
                successBlock = { response ->
                    // Todo
                    updateOptionCheck(index)
                },
                doAfterNetworkSuccess = {
                    pbGroup[index].isVisible = false
                },
                defaultErrorHandle = {
                    // Todo
                    pbGroup[index].isVisible = false
                },
                defaultExceptionHandle = {
                    // Todo
                    pbGroup[index].isVisible = false
                }
            )
        }
    }

    private fun updateOptionCheck(index: Int) {
        for ((i, iv) in checkGroup.withIndex()) {
            iv.isVisible = index == i
        }
        binding.disappearingOption6Arrow.isVisible = index != 6
        binding.disappearingOption6Interval.isVisible = index == 6
    }
}
