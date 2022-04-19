package one.mixin.android.job

import com.birbit.android.jobqueue.Params
import kotlinx.coroutines.runBlocking
import one.mixin.android.MixinApplication
import one.mixin.android.extension.fileSize
import one.mixin.android.extension.getFileNameNoEx
import one.mixin.android.extension.getMediaPath
import one.mixin.android.extension.isUUID
import timber.log.Timber

class StorageClearJob :
    BaseJob(Params(PRIORITY_UI_HIGH).groupBy(GROUP_ID).requireNetwork().persist()) {

    companion object {
        private const val serialVersionUID = 1L
        const val GROUP_ID = "convert_data_group"
    }

    override fun onAdded() {
    }

    override fun onRun() = runBlocking {
        val mediaPath = MixinApplication.appContext.getMediaPath()
        var size = 0L
        mediaPath?.listFiles()?.forEach { dir ->
            dir.listFiles()?.forEach {
                it.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val name = file.name.getFileNameNoEx()
                        if (name.isUUID() && messageDao.exists(name) == null) { // message's media file
                            size += file.length()
                            Timber.e("delete ${file.absolutePath} ${size.fileSize()}")
                        }
                    }
                }
            }
        }
        Timber.e("delete total: ${size.fileSize()}")
    }
}
