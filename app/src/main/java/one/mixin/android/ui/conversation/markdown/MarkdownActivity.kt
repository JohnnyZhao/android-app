package one.mixin.android.ui.conversation.markdown

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tbruyelle.rxpermissions2.RxPermissions
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.recycler.MarkwonAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.R
import one.mixin.android.databinding.ActivityMarkdownBinding
import one.mixin.android.databinding.ViewMarkdownBinding
import one.mixin.android.extension.*
import one.mixin.android.ui.common.BaseActivity
import one.mixin.android.ui.conversation.link.LinkBottomSheetDialogFragment
import one.mixin.android.ui.conversation.markdown.pdf.PDFGenerateListener
import one.mixin.android.ui.conversation.markdown.pdf.generatePDF
import one.mixin.android.ui.forward.ForwardActivity
import one.mixin.android.ui.web.WebActivity
import one.mixin.android.util.markdown.DefaultEntry
import one.mixin.android.util.markdown.MarkwonUtil
import one.mixin.android.util.markdown.SimpleEntry
import one.mixin.android.util.markdown.table.TableEntry
import one.mixin.android.vo.ForwardAction
import one.mixin.android.vo.ForwardMessage
import one.mixin.android.vo.ShareCategory
import one.mixin.android.widget.BottomSheet
import one.mixin.android.widget.WebControlView
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.FencedCodeBlock

@AndroidEntryPoint
class MarkdownActivity : BaseActivity() {
    private lateinit var binding: ActivityMarkdownBinding
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMarkdownBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.control.mode = this.isNightMode()
        binding.control.callback = object : WebControlView.Callback {
            override fun onMoreClick() {
                showBottomSheet()
            }

            override fun onCloseClick() {
                finish()
            }
        }
        val adapter = MarkwonAdapter.builder(
            DefaultEntry()
        ).include(
            FencedCodeBlock::class.java,
            SimpleEntry.create(
                R.layout.item_markdown_code_block,
                R.id.text
            )
        ).include(
            TableBlock::class.java,
            TableEntry.create { builder: TableEntry.Builder ->
                builder
                    .tableLayout(R.layout.item_markdown_table_block, R.id.table_layout)
                    .textLayoutIsRoot(R.layout.item_markdown_cell)
            }
        ).build()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.adapter = adapter
        val markwon = MarkwonUtil.getMarkwon(
            this,
            { link ->
                LinkBottomSheetDialogFragment.newInstance(link)
                    .showNow(supportFragmentManager, LinkBottomSheetDialogFragment.TAG)
            },
            { link ->
                WebActivity.show(this, link, intent.getStringExtra(CONVERSATION_ID))
            }
        )
        val markdown = intent.getStringExtra(CONTENT) ?: return
        adapter.setMarkdown(markwon, markdown)
        adapter.notifyDataSetChanged()
    }

    @SuppressLint("AutoDispose", "CheckResult")
    private fun showBottomSheet() {
        val builder = BottomSheet.Builder(this)
        val view = View.inflate(
            ContextThemeWrapper(this, R.style.Custom),
            R.layout.view_markdown,
            null
        )
        val viewBinding = ViewMarkdownBinding.bind(view)
        builder.setCustomView(view)
        val bottomSheet = builder.create()
        viewBinding.forward.setOnClickListener {
            val markdown = intent.getStringExtra(CONTENT) ?: return@setOnClickListener
            ForwardActivity.show(
                this,
                arrayListOf(ForwardMessage(ShareCategory.Post, markdown)),
                ForwardAction.App.Resultless()
            )
            bottomSheet.dismiss()
        }
        viewBinding.pdf.setOnClickListener {
            lifecycleScope.launch {
                val pdfFile = this@MarkdownActivity.getDocumentPath()
                    .createDocumentTemp("Test", "Test", "pdf")
                generatePDF(
                    binding.recyclerView,
                    pdfFile.absolutePath,
                    object :
                        PDFGenerateListener {
                        override fun pdfGenerationSuccess() {
                            this@MarkdownActivity.toast(
                                getString(
                                    R.string.save_to,
                                    pdfFile.absoluteFile
                                )
                            )
                        }

                        override fun pdfGenerationFailure(exception: Exception) {
                        }
                    })
            }
        }
        viewBinding.save.setOnClickListener {
            RxPermissions(this)
                .request(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                .subscribe(
                    { granted ->
                        if (granted) {
                            savePost {
                                bottomSheet.dismiss()
                            }
                        } else {
                            openPermissionSetting()
                        }
                    },
                    {
                    }
                )
        }
        bottomSheet.show()
    }

    private fun savePost(dismissAction: () -> Unit) {
        lifecycleScope.launch {
            val markdown = intent.getStringExtra(CONTENT) ?: return@launch
            try {
                withContext(Dispatchers.IO) {
                    val path = getPublicDocumentPath()
                    val file = path.createPostTemp()
                    file.outputStream().writer().use { writer ->
                        writer.write(markdown)
                    }
                    withContext(Dispatchers.Main) {
                        toast(getString(R.string.save_to, file.absolutePath))
                    }
                }
            } catch (e: Exception) {
                toast(R.string.save_failure)
            }
            dismissAction()
        }
    }

    companion object {
        private const val CONTENT = "content"
        private const val CONVERSATION_ID = "conversation_id"
        fun show(context: Context, content: String, conversationId: String? = null) {
            context.startActivity(
                Intent(context, MarkdownActivity::class.java).apply {
                    putExtra(CONTENT, content)
                    putExtra(CONVERSATION_ID, conversationId)
                }
            )
        }
    }
}
