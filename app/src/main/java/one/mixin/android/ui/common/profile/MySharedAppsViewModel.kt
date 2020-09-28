package one.mixin.android.ui.common.profile

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.mixin.android.repository.AccountRepository
import one.mixin.android.util.ErrorHandler

class MySharedAppsViewModel
@ViewModelInject internal constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {
    suspend fun addFavoriteApp(appId: String) =
        withContext(Dispatchers.IO) {
            val response = accountRepository.addFavoriteApp(appId)
            return@withContext if (response.isSuccess) {
                accountRepository.insertFavoriteApp(requireNotNull(response.data))
                true
            } else {
                ErrorHandler.handleMixinError(response.errorCode, response.errorDescription)
                false
            }
        }

    suspend fun refreshFavoriteApps(userId: String) = withContext(Dispatchers.IO) {
        val response = accountRepository.getUserFavoriteApps(userId)
        if (response.isSuccess) {
            response.data?.let { list ->
                accountRepository.insertFavoriteApps(userId, list)
            }
        }
    }

    suspend fun getFavoriteAppsByUserId(userId: String) = withContext(Dispatchers.IO) {
        accountRepository.getFavoriteAppsByUserId(userId)
    }

    suspend fun getUnfavoriteApps() = withContext(Dispatchers.IO) {
        accountRepository.getUnfavoriteApps()
    }

    suspend fun removeFavoriteApp(appId: String, userId: String) = withContext(Dispatchers.IO) {
        val response = accountRepository.removeFavoriteApp(appId)
        return@withContext if (response.isSuccess) {
            accountRepository.deleteByAppIdAndUserId(appId, userId)
            true
        } else {
            ErrorHandler.handleMixinError(response.errorCode, response.errorDescription)
            false
        }
    }

    suspend fun getApps() = withContext(Dispatchers.IO) {
        accountRepository.getApps()
    }
}
