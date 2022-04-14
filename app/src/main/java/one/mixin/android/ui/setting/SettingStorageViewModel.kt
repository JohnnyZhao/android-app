package one.mixin.android.ui.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.Constants
import one.mixin.android.Constants.Storage.AUDIO
import one.mixin.android.Constants.Storage.DATA
import one.mixin.android.Constants.Storage.IMAGE
import one.mixin.android.Constants.Storage.TRANSCRIPT
import one.mixin.android.Constants.Storage.VIDEO
import one.mixin.android.MixinApplication
import one.mixin.android.extension.FileSizeUnit
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.dirSize
import one.mixin.android.extension.fileSize
import one.mixin.android.extension.generateConversationPath
import one.mixin.android.extension.getAudioPath
import one.mixin.android.extension.getConversationAudioPath
import one.mixin.android.extension.getConversationDocumentPath
import one.mixin.android.extension.getConversationImagePath
import one.mixin.android.extension.getConversationMediaSize
import one.mixin.android.extension.getConversationVideoPath
import one.mixin.android.extension.getDocumentPath
import one.mixin.android.extension.getFileNameNoEx
import one.mixin.android.extension.getImagePath
import one.mixin.android.extension.getMediaPath
import one.mixin.android.extension.getStorageUsageByConversationAndType
import one.mixin.android.extension.getVideoPath
import one.mixin.android.extension.isUUID
import one.mixin.android.job.MixinJobManager
import one.mixin.android.job.TranscriptDeleteJob
import one.mixin.android.repository.ConversationRepository
import one.mixin.android.util.SINGLE_DB_THREAD
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.StorageUsage
import one.mixin.android.vo.absolutePath
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class SettingStorageViewModel
@Inject
internal constructor(
    private val conversationRepository: ConversationRepository,
    private val jobManager: MixinJobManager
) : ViewModel() {

    suspend fun getStorageUsage(context: Context, conversationId: String): List<StorageUsage> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StorageUsage>()
        context.getStorageUsageByConversationAndType(conversationId, IMAGE)?.apply {
            result.add(this)
        }
        context.getStorageUsageByConversationAndType(conversationId, VIDEO)?.apply {
            result.add(this)
        }
        context.getStorageUsageByConversationAndType(conversationId, AUDIO)?.apply {
            result.add(this)
        }
        context.getStorageUsageByConversationAndType(conversationId, DATA)?.apply {
            result.add(this)
        }
        conversationRepository.getMediaSizeTotalById(conversationId)?.apply {
            if (this > 0) {
                result.add(StorageUsage(conversationId, TRANSCRIPT, conversationRepository.countTranscriptById(conversationId), this / 1024))
            }
        }
        result.toList()
    }

    suspend fun getConversationStorageUsage(context: Context) = withContext(Dispatchers.IO) {
        conversationRepository.getConversationStorageUsage().asSequence().map { item ->
            item.apply { item.mediaSize = context.getConversationMediaSize(item.conversationId) }
        }.filter { conversationStorageUsage ->
            conversationStorageUsage.mediaSize != 0L && conversationStorageUsage.conversationId.isNotEmpty()
        }.sortedByDescending { conversationStorageUsage ->
            conversationStorageUsage.mediaSize
        }.toList().apply {
            val list = this
            viewModelScope.launch {
                val sum = list.sumOf { it.mediaSize }
                Timber.e("Local data: ${sum.fileSize(FileSizeUnit.KB)}")
                val mediaPath = context.getMediaPath()
                mediaPath?.let { m ->
                    val pathSize = m.dirSize() ?: 0
                    Timber.e("Media data: ${pathSize.fileSize(FileSizeUnit.KB)}")
                    Timber.e("Residual data: ${abs(pathSize - sum).fileSize(FileSizeUnit.KB)}")
                }
                var size = 0L
                mediaPath?.listFiles()?.forEach { dir ->
                    dir.listFiles()?.forEach {
                        it.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                val name = file.name.getFileNameNoEx()
                                // Timber.e(name)
                                if (name.isUUID() && conversationRepository.exists(name) == null) {
                                    size += file.length()
                                    Timber.e("delete ${file.absolutePath} ${size.fileSize()}")
                                }
                            } else {
                                // Timber.e("${file.absolutePath} dir")
                            }
                        }
                    }
                }
                Timber.e("delete total: ${size.fileSize()}")
            }
        }
    }

    fun clear(conversationId: String, type: String) {
        if (MixinApplication.appContext.defaultSharedPreferences.getBoolean(Constants.Account.PREF_ATTACHMENT, false)) {
            when (type) {
                IMAGE -> {
                    MixinApplication.get().getConversationImagePath(conversationId)?.deleteRecursively()
                    conversationRepository.deleteMediaMessageByConversationAndCategory(
                        conversationId,
                        MessageCategory.SIGNAL_IMAGE.name,
                        MessageCategory.PLAIN_IMAGE.name,
                        MessageCategory.ENCRYPTED_IMAGE.name
                    )
                }
                VIDEO -> {
                    MixinApplication.get().getConversationVideoPath(conversationId)?.deleteRecursively()
                    conversationRepository.deleteMediaMessageByConversationAndCategory(
                        conversationId,
                        MessageCategory.SIGNAL_VIDEO.name,
                        MessageCategory.PLAIN_VIDEO.name,
                        MessageCategory.ENCRYPTED_VIDEO.name
                    )
                }
                AUDIO -> {
                    MixinApplication.get().getConversationAudioPath(conversationId)?.deleteRecursively()
                    conversationRepository.deleteMediaMessageByConversationAndCategory(
                        conversationId,
                        MessageCategory.SIGNAL_AUDIO.name,
                        MessageCategory.PLAIN_AUDIO.name,
                        MessageCategory.ENCRYPTED_AUDIO.name
                    )
                }
                DATA -> {
                    MixinApplication.get().getConversationDocumentPath(conversationId)?.deleteRecursively()
                    conversationRepository.deleteMediaMessageByConversationAndCategory(
                        conversationId,
                        MessageCategory.SIGNAL_DATA.name,
                        MessageCategory.PLAIN_DATA.name,
                        MessageCategory.ENCRYPTED_DATA.name
                    )
                }
                TRANSCRIPT -> {
                    conversationRepository.deleteMediaMessageByConversationAndCategory(
                        conversationId,
                        MessageCategory.SIGNAL_TRANSCRIPT.name,
                        MessageCategory.PLAIN_TRANSCRIPT.name,
                        MessageCategory.ENCRYPTED_TRANSCRIPT.name
                    )
                }
            }
        } else {
            when (type) {
                IMAGE -> clear(conversationId, MessageCategory.SIGNAL_IMAGE.name, MessageCategory.PLAIN_IMAGE.name, MessageCategory.ENCRYPTED_IMAGE.name)
                VIDEO -> clear(conversationId, MessageCategory.SIGNAL_VIDEO.name, MessageCategory.PLAIN_VIDEO.name, MessageCategory.ENCRYPTED_VIDEO.name)
                AUDIO -> clear(conversationId, MessageCategory.SIGNAL_AUDIO.name, MessageCategory.PLAIN_AUDIO.name, MessageCategory.ENCRYPTED_AUDIO.name)
                DATA -> clear(conversationId, MessageCategory.SIGNAL_DATA.name, MessageCategory.PLAIN_DATA.name, MessageCategory.ENCRYPTED_DATA.name)
                TRANSCRIPT -> clear(conversationId, MessageCategory.SIGNAL_TRANSCRIPT.name, MessageCategory.PLAIN_TRANSCRIPT.name, MessageCategory.ENCRYPTED_TRANSCRIPT.name)
            }
        }
        conversationRepository.refreshConversationById(conversationId)
    }

    private fun clear(conversationId: String, signalCategory: String, plainCategory: String, encryptedCategory: String) {
        if (signalCategory == MessageCategory.SIGNAL_TRANSCRIPT.name && plainCategory == MessageCategory.PLAIN_TRANSCRIPT.name && plainCategory == MessageCategory.ENCRYPTED_TRANSCRIPT.name) {
            viewModelScope.launch(SINGLE_DB_THREAD) {
                val ids = conversationRepository.findTranscriptIdByConversationId(conversationId)
                if (ids.isEmpty()) {
                    return@launch
                }
                jobManager.addJobInBackground(TranscriptDeleteJob(ids))
            }
            return
        }
        conversationRepository.getMediaByConversationIdAndCategory(conversationId, signalCategory, plainCategory, encryptedCategory)
            ?.let { list ->
                list.forEach { item ->
                    conversationRepository.deleteMessage(item.messageId, item.absolutePath(MixinApplication.appContext, conversationId, item.mediaUrl))
                }
            }
        categoryPath(MixinApplication.appContext, signalCategory, conversationId)?.deleteRecursively()
    }

    private fun categoryPath(context: Context, category: String, conversationId: String): File? {
        return when {
            category.endsWith("_IMAGE") -> context.getImagePath().generateConversationPath(conversationId)
            category.endsWith("_VIDEO") -> context.getVideoPath().generateConversationPath(conversationId)
            category.endsWith("_AUDIO") -> context.getAudioPath().generateConversationPath(conversationId)
            category.endsWith("_DATA") -> context.getDocumentPath().generateConversationPath(conversationId)
            else -> null
        }
    }
}
