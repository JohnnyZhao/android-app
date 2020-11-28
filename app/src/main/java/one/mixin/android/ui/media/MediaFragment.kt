package one.mixin.android.ui.media

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ViewAnimator
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import one.mixin.android.Constants.ARGS_CONVERSATION_ID
import one.mixin.android.R
import one.mixin.android.databinding.LayoutRecyclerViewBinding
import one.mixin.android.extension.realSize
import one.mixin.android.extension.withArgs
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.common.recyclerview.StickyRecyclerHeadersDecorationForGrid
import one.mixin.android.ui.conversation.adapter.StickerSpacingItemDecoration
import one.mixin.android.ui.media.pager.MediaPagerActivity
import org.jetbrains.anko.dip

@AndroidEntryPoint
class MediaFragment : BaseFragment() {
    companion object {
        const val TAG = "MediaFragment"
        const val PADDING = 1
        const val COLUMN = 4

        fun newInstance(conversationId: String) = MediaFragment().withArgs {
            putString(ARGS_CONVERSATION_ID, conversationId)
        }
    }

    private val conversationId: String by lazy {
        requireArguments().getString(ARGS_CONVERSATION_ID)!!
    }

    private val padding: Int by lazy {
        requireContext().dip(PADDING)
    }

    private val adapter = MediaAdapter(
        fun(imageView: View, messageId: String) {
            MediaPagerActivity.show(requireActivity(), imageView, conversationId, messageId, true)
        }
    )

    private val viewModel by viewModels<SharedMediaViewModel>()

    private var _binding: LayoutRecyclerViewBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutRecyclerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter.size = (requireContext().realSize().x - (COLUMN - 1) * padding) / COLUMN
        val lm = GridLayoutManager(requireContext(), COLUMN)
        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (different2Next(position)) {
                    val offset = getSameIdItemCount(position)
                    val columnIndex = offset.rem(COLUMN)
                    val extraColumn = COLUMN - (columnIndex + 1)
                    1 + extraColumn
                } else {
                    1
                }
            }
        }
        binding.recyclerView.layoutManager = lm
        binding.recyclerView.itemAnimator = null
        binding.recyclerView.isVerticalScrollBarEnabled = false
        binding.recyclerView.addItemDecoration(StickerSpacingItemDecoration(COLUMN, padding, false))
        binding.recyclerView.addItemDecoration(StickyRecyclerHeadersDecorationForGrid(adapter, COLUMN))
        binding.recyclerView.adapter = adapter
        binding.emptyIv.setImageResource(R.drawable.ic_empty_media)
        binding.emptyTv.setText(R.string.no_media)
        viewModel.getMediaMessagesExcludeLive(conversationId).observe(
            viewLifecycleOwner,
            {
                if (it.size <= 0) {
                    (view as ViewAnimator).displayedChild = 1
                } else {
                    (view as ViewAnimator).displayedChild = 0
                }
                adapter.submitList(it)
            }
        )
    }

    private fun different2Next(pos: Int): Boolean {
        val headerId = adapter.getHeaderId(pos)
        var nextHeaderId = -1L
        val nextPos = pos + 1
        if (nextPos >= 0 && nextPos < adapter.itemCount) {
            nextHeaderId = adapter.getHeaderId(nextPos)
        }
        return headerId != nextHeaderId
    }

    private fun getSameIdItemCount(pos: Int): Int {
        val currentId = adapter.getHeaderId(pos)
        for (i in 1 until pos) {
            if (adapter.getHeaderId(pos - i) != currentId) {
                return i - 1
            }
        }
        return pos
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
