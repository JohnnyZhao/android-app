package one.mixin.android.ui.conversation.link

import android.annotation.SuppressLint
import android.app.Dialog
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.net.toUri
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.manager.SupportRequestManagerFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.Constants.Scheme
import one.mixin.android.R
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.request.TransferRequest
import one.mixin.android.api.response.AuthorizationResponse
import one.mixin.android.api.response.ConversationResponse
import one.mixin.android.api.response.MultisigsResponse
import one.mixin.android.api.response.NonFungibleOutputResponse
import one.mixin.android.api.response.PaymentCodeResponse
import one.mixin.android.api.response.getScopes
import one.mixin.android.databinding.FragmentBottomSheetBinding
import one.mixin.android.extension.appendQueryParamsFromOtherUri
import one.mixin.android.extension.booleanFromAttribute
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.getGroupAvatarPath
import one.mixin.android.extension.handleSchemeSend
import one.mixin.android.extension.isDonateUrl
import one.mixin.android.extension.isUUID
import one.mixin.android.extension.toast
import one.mixin.android.extension.withArgs
import one.mixin.android.job.getIconUrlName
import one.mixin.android.session.Session
import one.mixin.android.ui.auth.AuthBottomSheetDialogFragment
import one.mixin.android.ui.common.BottomSheetViewModel
import one.mixin.android.ui.common.JoinGroupBottomSheetDialogFragment
import one.mixin.android.ui.common.JoinGroupConversation
import one.mixin.android.ui.common.MultisigsBottomSheetDialogFragment
import one.mixin.android.ui.common.NftBottomSheetDialogFragment
import one.mixin.android.ui.common.QrScanBottomSheetDialogFragment
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.common.biometric.AssetBiometricItem
import one.mixin.android.ui.common.biometric.Multi2MultiBiometricItem
import one.mixin.android.ui.common.biometric.NftBiometricItem
import one.mixin.android.ui.common.biometric.One2MultiBiometricItem
import one.mixin.android.ui.common.biometric.TransferBiometricItem
import one.mixin.android.ui.common.biometric.WithdrawBiometricItem
import one.mixin.android.ui.conversation.ConversationActivity
import one.mixin.android.ui.conversation.PreconditionBottomSheetDialogFragment
import one.mixin.android.ui.conversation.PreconditionBottomSheetDialogFragment.Companion.FROM_LINK
import one.mixin.android.ui.conversation.TransferFragment
import one.mixin.android.ui.conversation.transfer.TransferBottomSheetDialogFragment
import one.mixin.android.ui.home.MainActivity
import one.mixin.android.ui.url.UrlInterpreterActivity
import one.mixin.android.ui.wallet.PinAddrBottomSheetDialogFragment
import one.mixin.android.ui.wallet.TransactionBottomSheetDialogFragment
import one.mixin.android.ui.web.WebActivity
import one.mixin.android.util.SystemUIManager
import one.mixin.android.util.viewBinding
import one.mixin.android.vo.Address
import one.mixin.android.vo.AssetItem
import one.mixin.android.vo.User
import one.mixin.android.vo.generateConversationId
import timber.log.Timber
import java.util.UUID

@AndroidEntryPoint
class LinkBottomSheetDialogFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "LinkBottomSheetDialogFragment"
        const val CODE = "code"

        fun newInstance(code: String) = LinkBottomSheetDialogFragment().withArgs {
            putString(CODE, code)
        }
    }

    private val scopeProvider by lazy { AndroidLifecycleScopeProvider.from(this, Lifecycle.Event.ON_STOP) }

    private var authOrPay = false

    override fun getTheme() = R.style.AppTheme_Dialog

    private val linkViewModel by viewModels<BottomSheetViewModel>()

    private val binding by viewBinding(FragmentBottomSheetBinding::inflate)

    private lateinit var code: String
    private lateinit var contentView: View

    private val url: String by lazy { requireArguments().getString(CODE)!! }

    override fun onStart() {
        try {
            super.onStart()
        } catch (ignored: WindowManager.BadTokenException) {
        }
        dialog?.window?.let { window ->
            SystemUIManager.lightUI(
                window,
                !requireContext().booleanFromAttribute(R.attr.flag_night)
            )
        }
    }

    private fun getUserOrAppNotFoundTip(isApp: Boolean) = if (isApp) R.string.error_app_not_found else R.string.error_user_not_found

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        if (Build.VERSION.SDK_INT >= 26) {
            dialog.window?.decorView?.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        contentView = binding.root
        dialog.setContentView(contentView)
        val behavior = ((contentView.parent as View).layoutParams as? CoordinatorLayout.LayoutParams)?.behavior
        if (behavior != null && behavior is BottomSheetBehavior<*>) {
            behavior.peekHeight = requireContext().dpToPx(300f)
            behavior.addBottomSheetCallback(mBottomSheetBehaviorCallback)
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, requireContext().dpToPx(300f))
            dialog.window?.setGravity(Gravity.BOTTOM)
        }

        val isUserScheme = url.startsWith(Scheme.USERS, true) || url.startsWith(Scheme.HTTPS_USERS, true)
        val isAppScheme = url.startsWith(Scheme.APPS, true) || url.startsWith(Scheme.HTTPS_APPS, true)
        if (isUserScheme || isAppScheme) {
            val uri = url.toUri()
            val segments = uri.pathSegments
            val userId = if (segments.size >= 2) {
                segments[1]
            } else {
                segments[0]
            }
            if (!userId.isUUID()) {
                toast(getUserOrAppNotFoundTip(isAppScheme))
                dismiss()
            } else {
                lifecycleScope.launch {
                    var user = linkViewModel.suspendFindUserById(userId)
                    if (user == null) {
                        val response = try {
                            withContext(Dispatchers.IO) {
                                linkViewModel.getUser(userId).execute()
                            }
                        } catch (t: Throwable) {
                            toast(getUserOrAppNotFoundTip(isAppScheme))
                            dismiss()
                            return@launch
                        }
                        if (response.isSuccessful) {
                            user = response.body()?.data
                        }
                        if (user == null) {
                            toast(getUserOrAppNotFoundTip(isAppScheme))
                            dismiss()
                            return@launch
                        }
                    }
                    val isOpenApp = isAppScheme && uri.getQueryParameter("action") == "open"
                    if (isOpenApp && user.appId != null) {
                        lifecycleScope.launch {
                            val app = linkViewModel.findAppById(user!!.appId!!)
                            if (app != null) {
                                val url = try {
                                    app.homeUri.appendQueryParamsFromOtherUri(uri)
                                } catch (e: Exception) {
                                    app.homeUri
                                }
                                WebActivity.show(requireActivity(), url, null, app)
                            } else {
                                UserBottomSheetDialogFragment.newInstance(user!!)
                                    .showNow(parentFragmentManager, UserBottomSheetDialogFragment.TAG)
                            }
                        }
                    } else {
                        UserBottomSheetDialogFragment.newInstance(user)
                            .showNow(parentFragmentManager, UserBottomSheetDialogFragment.TAG)
                    }
                    dismiss()
                }
            }
        } else if (url.startsWith(Scheme.TRANSFER, true) || url.startsWith(Scheme.HTTPS_TRANSFER, true)) {
            if (checkHasPin()) return

            val uri = url.toUri()
            val segments = uri.pathSegments
            val userId = if (segments.size >= 2) {
                segments[1]
            } else {
                segments[0]
            }
            if (!userId.isUUID()) {
                toast(R.string.error_user_not_found)
                dismiss()
            } else if (userId == Session.getAccountId()) {
                toast(R.string.cant_transfer_self)
                dismiss()
            } else {
                lifecycleScope.launch {
                    var user = linkViewModel.suspendFindUserById(userId)
                    if (user == null) {
                        val response = try {
                            withContext(Dispatchers.IO) {
                                linkViewModel.getUser(userId).execute()
                            }
                        } catch (t: Throwable) {
                            toast(R.string.error_user_not_found)
                            dismiss()
                            return@launch
                        }
                        if (response.isSuccessful) {
                            user = response.body()?.data
                        }
                        if (user == null) {
                            toast(R.string.error_user_not_found)
                            dismiss()
                            return@launch
                        }
                    }
                    TransferFragment.newInstance(userId, supportSwitchAsset = true)
                        .showNow(parentFragmentManager, TransferFragment.TAG)
                    dismiss()
                }
            }
        } else if (url.startsWith(Scheme.HTTPS_PAY, true) ||
            url.startsWith(Scheme.PAY, true)
        ) {
            if (checkHasPin()) return

            lifecycleScope.launch {
                if (!showTransfer(url)) {
                    showError(R.string.bottom_sheet_invalid_payment)
                } else {
                    dismiss()
                }
            }
        } else if (url.startsWith(Scheme.HTTPS_CODES, true) || url.startsWith(Scheme.CODES, true)) {
            val segments = Uri.parse(url).pathSegments
            code = if (segments.size >= 2) {
                segments[1]
            } else {
                segments[0]
            }
            lifecycleScope.launch {
                val response = linkViewModel.searchCode(code)
                if (!response.isSuccess) {
                    withContext(Dispatchers.Main) {
                        showError()
                    }
                }
                val data = response.data
                if (data is ConversationResponse) {
                    val found = data.participants.find { it.userId == Session.getAccountId() }
                    if (found != null) {
                        linkViewModel.refreshConversation(data.conversationId)
                        withContext(Dispatchers.Main) {
                            toast(R.string.group_already_in)
                            context?.let { ConversationActivity.show(it, data.conversationId) }
                            dismiss()
                        }
                    } else {
                        val avatarUserIds = mutableListOf<String>()
                        val notExistsUserIdList = mutableListOf<String>()
                        for (p in data.participants) {
                            val u = linkViewModel.suspendFindUserById(p.userId)
                            if (u == null) {
                                notExistsUserIdList.add(p.userId)
                            }
                            if (avatarUserIds.size < 4) {
                                avatarUserIds.add(p.userId)
                            }
                        }
                        val avatar4List = avatarUserIds.take(4)
                        val iconUrl = if (notExistsUserIdList.isNotEmpty()) {
                            linkViewModel.refreshUsers(
                                notExistsUserIdList,
                                data.conversationId,
                                avatar4List
                            )
                            null
                        } else {
                            val avatarUsers =
                                linkViewModel.findMultiUsersByIds(avatar4List.toSet())
                            linkViewModel.startGenerateAvatar(
                                data.conversationId,
                                avatar4List
                            )

                            val name = getIconUrlName(data.conversationId, avatarUsers)
                            val f = requireContext().getGroupAvatarPath(name, false)
                            f.absolutePath
                        }
                        val joinGroupConversation = JoinGroupConversation(
                            data.conversationId,
                            data.name,
                            data.announcement,
                            data.participants.size,
                            iconUrl
                        )
                        JoinGroupBottomSheetDialogFragment.newInstance(
                            joinGroupConversation,
                            code
                        )
                            .showNow(
                                parentFragmentManager,
                                JoinGroupBottomSheetDialogFragment.TAG
                            )
                        dismiss()
                    }
                } else if (data is AuthorizationResponse) {
                    val assets = linkViewModel.simpleAssetsWithBalance()
                    activity?.let {
                        val scopes = data.getScopes(it, assets)
                        AuthBottomSheetDialogFragment.newInstance(scopes, data)
                            .showNow(parentFragmentManager, AuthBottomSheetDialogFragment.TAG)
                        authOrPay = true
                        dismiss()
                    }
                } else if (data is User) {
                    val account = Session.getAccount()
                    if (account != null && account.userId == data.userId) {
                        withContext(Dispatchers.Main) {
                            toast("It's your QR Code, please try another.")
                        }
                    } else {
                        UserBottomSheetDialogFragment.newInstance(data)
                            .showNow(parentFragmentManager, UserBottomSheetDialogFragment.TAG)
                    }
                    dismiss()
                } else if (data is MultisigsResponse) {
                    if (checkHasPin()) return@launch
                    val multisigs = data
                    val asset = checkAsset(multisigs.assetId)
                    if (asset != null) {
                        val multisigsBiometricItem = Multi2MultiBiometricItem(
                            requestId = multisigs.requestId,
                            action = multisigs.action,
                            senders = multisigs.senders,
                            receivers = multisigs.receivers,
                            threshold = multisigs.threshold,
                            asset = asset,
                            amount = multisigs.amount,
                            pin = null,
                            traceId = null,
                            memo = multisigs.memo,
                            state = multisigs.state
                        )
                        MultisigsBottomSheetDialogFragment.newInstance(multisigsBiometricItem)
                            .showNow(
                                parentFragmentManager,
                                MultisigsBottomSheetDialogFragment.TAG
                            )
                        dismiss()
                    } else {
                        withContext(Dispatchers.Main) {
                            showError()
                        }
                    }
                } else if (data is NonFungibleOutputResponse) {
                    if (checkHasPin()) return@launch
                    val nftBiometricItem = NftBiometricItem(
                        requestId = data.requestId,
                        action = data.action,
                        tokenId = data.tokenId,
                        senders = data.senders,
                        receivers = data.receivers,
                        sendersThreshold = data.sendersThreshold,
                        receiversThreshold = data.receiversThreshold,
                        rawTransaction = data.rawTransaction,
                        amount = data.amount,
                        pin = null,
                        memo = data.memo,
                        state = data.state
                    )
                    NftBottomSheetDialogFragment.newInstance(nftBiometricItem)
                        .showNow(parentFragmentManager, NftBottomSheetDialogFragment.TAG)
                    dismiss()
                } else if (data is PaymentCodeResponse) {
                    if (checkHasPin()) return@launch
                    val asset = checkAsset(data.assetId)
                    if (asset != null) {
                        val multisigsBiometricItem = One2MultiBiometricItem(
                            threshold = data.threshold,
                            senders = arrayOf(Session.getAccountId()!!),
                            receivers = data.receivers,
                            asset = asset,
                            amount = data.amount,
                            pin = null,
                            traceId = data.traceId,
                            memo = data.memo,
                            state = data.status
                        )
                        MultisigsBottomSheetDialogFragment.newInstance(multisigsBiometricItem)
                            .showNow(
                                parentFragmentManager,
                                MultisigsBottomSheetDialogFragment.TAG
                            )
                        dismiss()
                    } else {
                        withContext(Dispatchers.Main) {
                            showError()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showError()
                    }
                }
            }
        } else if (url.startsWith(Scheme.HTTPS_ADDRESS, true) || url.startsWith(Scheme.ADDRESS, true)) {
            if (checkHasPin()) return

            val uri = Uri.parse(url)
            val action = uri.getQueryParameter("action")
            if (action != null && action == "delete") {
                val assetId = uri.getQueryParameter("asset")
                val addressId = uri.getQueryParameter("address")
                if (assetId != null && assetId.isUUID() && addressId != null && addressId.isUUID()) {
                    lifecycleScope.launch {
                        val pair = linkViewModel.findAddressById(addressId, assetId)
                        val address = pair.first
                        if (pair.second) {
                            showError(R.string.error_address_exists)
                        } else if (address == null) {
                            showError(R.string.error_address_not_sync)
                        } else {
                            val asset = checkAsset(assetId)
                            if (asset != null) {
                                PinAddrBottomSheetDialogFragment.newInstance(
                                    assetId = assetId,
                                    assetUrl = asset.iconUrl,
                                    assetSymbol = asset.symbol,
                                    chainId = asset.chainId,
                                    chainName = asset.chainName,
                                    chainIconUrl = asset.chainIconUrl,
                                    assetName = asset.name,
                                    addressId = addressId,
                                    label = address.label,
                                    destination = address.destination,
                                    tag = address.tag,
                                    type = PinAddrBottomSheetDialogFragment.DELETE
                                ).showNow(this@LinkBottomSheetDialogFragment.parentFragmentManager, PinAddrBottomSheetDialogFragment.TAG)
                                dismiss()
                            } else {
                                showError()
                            }
                        }
                    }
                } else {
                    showError()
                }
            } else {
                val assetId = uri.getQueryParameter("asset")
                val destination = uri.getQueryParameter("destination")
                val label = uri.getQueryParameter("label").run {
                    Uri.decode(this)
                }
                val tag = uri.getQueryParameter("tag").run {
                    Uri.decode(this)
                }
                if (assetId != null && assetId.isUUID() && !destination.isNullOrEmpty() && !label.isNullOrEmpty()) {
                    lifecycleScope.launch {
                        val asset = checkAsset(assetId)
                        if (asset != null) {
                            PinAddrBottomSheetDialogFragment.newInstance(
                                assetId = assetId,
                                assetUrl = asset.iconUrl,
                                assetSymbol = asset.symbol,
                                chainId = asset.chainId,
                                chainName = asset.chainName,
                                chainIconUrl = asset.chainIconUrl,
                                assetName = asset.name,
                                label = label,
                                destination = destination,
                                tag = tag,
                                type = PinAddrBottomSheetDialogFragment.ADD
                            )
                                .showNow(this@LinkBottomSheetDialogFragment.parentFragmentManager, PinAddrBottomSheetDialogFragment.TAG)
                            dismiss()
                        } else {
                            showError()
                        }
                    }
                } else {
                    showError()
                }
            }
        } else if (url.startsWith(Scheme.SNAPSHOTS, true)) {
            if (checkHasPin()) return

            val uri = Uri.parse(url)
            val traceId = uri.getQueryParameter("trace")
            if (!traceId.isNullOrEmpty() && traceId.isUUID()) {
                lifecycleScope.launch {
                    val result = linkViewModel.getSnapshotByTraceId(traceId)
                    if (result != null) {
                        dismiss()
                        TransactionBottomSheetDialogFragment.newInstance(result.first, result.second)
                            .show(parentFragmentManager, TransactionBottomSheetDialogFragment.TAG)
                    } else {
                        showError()
                    }
                }
                return
            }
            val snapshotId = uri.lastPathSegment
            if (snapshotId.isNullOrEmpty() || !snapshotId.isUUID()) {
                showError()
            } else {
                lifecycleScope.launch {
                    val result = linkViewModel.getSnapshotAndAsset(snapshotId)
                    if (result != null) {
                        dismiss()
                        TransactionBottomSheetDialogFragment.newInstance(result.first, result.second)
                            .show(parentFragmentManager, TransactionBottomSheetDialogFragment.TAG)
                    } else {
                        showError()
                    }
                }
            }
        } else if (url.startsWith(Scheme.HTTPS_WITHDRAWAL, true) || url.startsWith(Scheme.WITHDRAWAL, true)) {
            if (checkHasPin()) return

            val uri = Uri.parse(url)
            val assetId = uri.getQueryParameter("asset")
            val amount = uri.getQueryParameter("amount")
            val memo = uri.getQueryParameter("memo")?.run {
                Uri.decode(this)
            }
            val traceId = uri.getQueryParameter("trace")
            val addressId = uri.getQueryParameter("address")
            if (assetId.isNullOrEmpty() || addressId.isNullOrEmpty() ||
                amount.isNullOrEmpty() || traceId.isNullOrEmpty() || !assetId.isUUID() ||
                !traceId.isUUID()
            ) {
                showError()
            } else {
                lifecycleScope.launch {
                    val pair = linkViewModel.findAddressById(addressId, assetId)
                    val address = pair.first
                    val asset = checkAsset(assetId)
                    if (asset != null) {
                        when {
                            pair.second -> {
                                showError(R.string.error_address_exists)
                            }
                            address == null -> {
                                showError(R.string.error_address_not_sync)
                            }
                            else -> {
                                val dust = address.dust?.toDoubleOrNull()
                                val amountDouble = amount.toDoubleOrNull()
                                if (dust != null && amountDouble != null && amountDouble < dust) {
                                    val errorString = getString(R.string.bottom_withdrawal_least_tip, address.dust, asset.symbol)
                                    showError(errorString)
                                    toast(errorString)
                                    return@launch
                                }

                                val transferRequest = TransferRequest(assetId, null, amount, null, traceId, memo, addressId)
                                handleMixinResponse(
                                    invokeNetwork = {
                                        linkViewModel.paySuspend(transferRequest)
                                    },
                                    successBlock = { r ->
                                        val response = r.data ?: return@handleMixinResponse false

                                        showWithdrawalBottom(address, amount, asset, traceId, response.status, memo)
                                    },
                                    failureBlock = {
                                        showError(R.string.bottom_sheet_invalid_payment)
                                        return@handleMixinResponse false
                                    },
                                    exceptionBlock = {
                                        showError(R.string.bottom_sheet_check_payment_info)
                                        return@handleMixinResponse false
                                    }
                                )
                            }
                        }
                    } else {
                        showError(R.string.error_asset_exists)
                    }
                }
            }
        } else if (url.isDonateUrl()) {
            if (checkHasPin()) return

            lifecycleScope.launch {
                val newUrl = url.replaceFirst(":", "://")
                if (!showTransfer(newUrl)) {
                    QrScanBottomSheetDialogFragment.newInstance(url)
                        .show(parentFragmentManager, QrScanBottomSheetDialogFragment.TAG)
                }
                dismiss()
            }
        } else if (url.startsWith(Scheme.CONVERSATIONS, true)) {
            val uri = Uri.parse(url)
            val conversationId = uri.pathSegments[0]
            if (conversationId.isEmpty() || !conversationId.isUUID()) {
                showError()
                return
            }
            val userId = uri.getQueryParameter("user")
            lifecycleScope.launch {
                if (userId != null) {
                    val user = linkViewModel.refreshUser(userId)
                    when {
                        user == null -> {
                            showError(R.string.error_user_not_found)
                        }
                        conversationId != generateConversationId(requireNotNull(Session.getAccountId()), userId) -> {
                            showError()
                        }
                        else -> {
                            ConversationActivity.show(requireContext(), conversationId, userId)
                            dismiss()
                        }
                    }
                } else {
                    val conversation = linkViewModel.getAndSyncConversation(conversationId)
                    if (conversation != null) {
                        ConversationActivity.show(requireContext(), conversation.conversationId)
                        dismiss()
                    } else {
                        showError(R.string.error_conversation_not_found)
                    }
                }
            }
        } else if (url.startsWith(Scheme.SEND, true)) {
            val uri = Uri.parse(url)
            uri.handleSchemeSend(
                requireContext(),
                parentFragmentManager,
                showNow = false,
                afterShareText = { dismiss() },
                afterShareData = { dismiss() },
                onError = { err ->
                    showError(err)
                }
            )
        } else {
            showError()
        }
    }

    private fun checkHasPin(): Boolean {
        if (Session.getAccount()?.hasPin == false) {
            MainActivity.showWallet(requireContext())
            dismiss()
            return true
        }
        return false
    }

    override fun dismiss() {
        if (isAdded) {
            try {
                super.dismiss()
            } catch (e: IllegalStateException) {
                Timber.w(e)
            }
        }
    }

    override fun showNow(manager: FragmentManager, tag: String?) {
        try {
            super.showNow(manager, tag)
        } catch (e: IllegalStateException) {
            Timber.w(e)
        }
    }

    override fun onDetach() {
        super.onDetach()
        if (activity is UrlInterpreterActivity) {
            var realFragmentCount = 0
            parentFragmentManager.fragments.forEach { f ->
                if (f !is SupportRequestManagerFragment) {
                    realFragmentCount++
                }
            }
            if (realFragmentCount <= 0) {
                activity?.finish()
            }
        }
    }

    private suspend fun showTransfer(text: String): Boolean {
        val uri = text.toUri()
        val amount = uri.getQueryParameter("amount")?.toDoubleOrNull()?.toString() ?: return false
        val userId = uri.getQueryParameter("recipient")
        if (userId == null || !userId.isUUID()) {
            return false
        }
        val assetId = uri.getQueryParameter("asset")
        if (assetId == null || !assetId.isUUID()) {
            return false
        }
        val trace = uri.getQueryParameter("trace") ?: UUID.randomUUID().toString()
        val memo = uri.getQueryParameter("memo")

        val asset: AssetItem = checkAsset(assetId) ?: return false

        val user = linkViewModel.refreshUser(userId) ?: return false

        val transferRequest = TransferRequest(assetId, userId, amount, null, trace, memo)
        return handleMixinResponse(
            invokeNetwork = {
                linkViewModel.paySuspend(transferRequest)
            },
            successBlock = { r ->
                val response = r.data ?: return@handleMixinResponse false

                showTransferBottom(user, amount, asset, trace, response.status, memo)
                return@handleMixinResponse true
            }
        ) ?: false
    }

    private suspend fun showTransferBottom(user: User, amount: String, asset: AssetItem, traceId: String, status: String, memo: String?) {
        val pair = linkViewModel.findLatestTrace(user.userId, null, null, amount, asset.assetId)
        if (pair.second) {
            showError(getString(R.string.bottom_sheet_check_trace_failed))
            return
        }
        val biometricItem = TransferBiometricItem(user, asset, amount, null, traceId, memo, status, pair.first)
        showPreconditionBottom(biometricItem)
    }

    private suspend fun showWithdrawalBottom(address: Address, amount: String, asset: AssetItem, traceId: String, status: String, memo: String?) {
        val pair = linkViewModel.findLatestTrace(null, address.destination, address.tag, amount, asset.assetId)
        if (pair.second) {
            showError(getString(R.string.bottom_sheet_check_trace_failed))
            return
        }
        val biometricItem = WithdrawBiometricItem(
            address.destination, address.tag, address.addressId, address.label, address.fee,
            asset, amount, null, traceId, memo, status, pair.first
        )
        showPreconditionBottom(biometricItem)
    }

    private fun showPreconditionBottom(biometricItem: AssetBiometricItem) {
        val preconditionBottom = PreconditionBottomSheetDialogFragment.newInstance(biometricItem, FROM_LINK)
        preconditionBottom.callback = object : PreconditionBottomSheetDialogFragment.Callback {
            override fun onSuccess() {
                val bottom = TransferBottomSheetDialogFragment.newInstance(biometricItem)
                bottom.show(preconditionBottom.parentFragmentManager, TransferBottomSheetDialogFragment.TAG)
                dismiss()
            }

            override fun onCancel() {
                dismiss()
            }
        }
        preconditionBottom.showNow(parentFragmentManager, PreconditionBottomSheetDialogFragment.TAG)
    }

    private suspend fun checkAsset(assetId: String): AssetItem? {
        val asset = linkViewModel.findAssetItemById(assetId)
        if (asset == null) {
            linkViewModel.refreshAsset(assetId)
        } else if (asset.assetId != asset.chainId && linkViewModel.findAssetItemById(asset.chainId) == null) {
            linkViewModel.refreshAsset(asset.chainId)
        }
        return linkViewModel.findAssetItemById(assetId)
    }

    private fun showError(@StringRes errorRes: Int = R.string.link_error) {
        binding.apply {
            linkErrorInfo.setText(errorRes)
            linkLoading.visibility = GONE
            linkErrorInfo.visibility = VISIBLE
        }
    }

    private fun showError(error: String) {
        binding.apply {
            linkErrorInfo.text = error
            linkLoading.visibility = GONE
            linkErrorInfo.visibility = VISIBLE
        }
    }

    private val mBottomSheetBehaviorCallback = object : BottomSheetBehavior.BottomSheetCallback() {

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                try {
                    dismissAllowingStateLoss()
                } catch (e: IllegalStateException) {
                    Timber.w(e)
                }
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    }
}
