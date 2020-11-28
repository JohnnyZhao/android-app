package one.mixin.android.ui.media

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ViewAnimator
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration
import dagger.hilt.android.AndroidEntryPoint
import one.mixin.android.Constants
import one.mixin.android.R
import one.mixin.android.databinding.LayoutRecyclerViewBinding
import one.mixin.android.extension.withArgs
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.conversation.markdown.MarkdownActivity
import one.mixin.android.vo.MessageItem

@AndroidEntryPoint
class PostFragment : BaseFragment() {
    companion object {
        const val TAG = "PostFragment"

        fun newInstance(conversationId: String) = PostFragment().withArgs {
            putString(Constants.ARGS_CONVERSATION_ID, conversationId)
        }
    }

    private val conversationId: String by lazy {
        requireArguments().getString(Constants.ARGS_CONVERSATION_ID)!!
    }

    private val adapter by lazy {
        PostAdapter(
            requireActivity(),
            fun(messageItem: MessageItem) {
                MarkdownActivity.show(requireActivity(), messageItem.content!!, conversationId)
            }
        )
    }

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
        binding.emptyIv.setImageResource(R.drawable.ic_empty_file)
        binding.emptyTv.setText(R.string.no_post)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(StickyRecyclerHeadersDecoration(adapter))
        binding.recyclerView.adapter = adapter
        viewModel.getPostMessages(conversationId).observe(
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
