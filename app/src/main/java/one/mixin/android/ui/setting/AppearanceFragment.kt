package one.mixin.android.ui.setting

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.AndroidEntryPoint
import one.mixin.android.Constants
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.databinding.FragmentAppearanceBinding
import one.mixin.android.extension.alertDialogBuilder
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.putInt
import one.mixin.android.extension.textColorResource
import one.mixin.android.session.Session
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.util.SystemUIManager
import one.mixin.android.util.TimeCache
import one.mixin.android.util.language.Lingver
import one.mixin.android.util.viewBinding
import one.mixin.android.vo.Fiats
import one.mixin.android.widget.theme.Coordinate
import one.mixin.android.widget.theme.NightModeSwitch.Companion.ANIM_DURATION
import one.mixin.android.widget.theme.ThemeActivity
import java.util.Locale

@AndroidEntryPoint
class AppearanceFragment : BaseFragment(R.layout.fragment_appearance) {
    companion object {
        const val TAG = "AppearanceFragment"

        const val POS_FOLLOW_SYSTEM = 0
        const val POS_ENGLISH = 1
        const val POS_SIMPLIFY_CHINESE = 2
        const val POS_INDONESIA = 3
        const val POS_Malay = 4

        fun newInstance() = AppearanceFragment()
    }

    private val binding by viewBinding(FragmentAppearanceBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            titleView.leftIb.setOnClickListener {
                activity?.onBackPressed()
            }
            nightModeRl.setOnClickListener {
                nightModeSwitch.switch()
            }
            nightModeTv.setText(R.string.setting_theme)
            nightModeSwitch.initState(
                defaultSharedPreferences.getInt(
                    Constants.Theme.THEME_CURRENT_ID,
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        Constants.Theme.THEME_LIGHT_ID
                    } else {
                        Constants.Theme.THEME_AUTO_ID
                    }
                )
            )
            nightModeSwitch.setOnSwitchListener { state ->
                if (!isAdded) return@setOnSwitchListener
                val currentId = defaultSharedPreferences.getInt(
                    Constants.Theme.THEME_CURRENT_ID,
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        Constants.Theme.THEME_LIGHT_ID
                    } else {
                        Constants.Theme.THEME_AUTO_ID
                    }
                )
                if (currentId == state) return@setOnSwitchListener

                val currentNightMode = if (currentId == Constants.Theme.THEME_AUTO_ID) {
                    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                } else currentId == Constants.Theme.THEME_NIGHT_ID

                val targetNightMode = if (state == Constants.Theme.THEME_AUTO_ID) {
                    MixinApplication.appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                } else state == Constants.Theme.THEME_NIGHT_ID
                if (currentNightMode != targetNightMode) {
                    (requireActivity() as ThemeActivity).run {
                        changeTheme(
                            getViewCoordinates(nightModeSwitch),
                            ANIM_DURATION,
                            !targetNightMode
                        ) {
                            AppCompatDelegate.setDefaultNightMode(
                                when (state) {
                                    Constants.Theme.THEME_LIGHT_ID -> AppCompatDelegate.MODE_NIGHT_NO
                                    Constants.Theme.THEME_NIGHT_ID -> AppCompatDelegate.MODE_NIGHT_YES
                                    Constants.Theme.THEME_AUTO_ID -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                                    else -> AppCompatDelegate.MODE_NIGHT_NO
                                }
                            )
                            defaultSharedPreferences.putInt(Constants.Theme.THEME_CURRENT_ID, state)
                            if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
                                requireActivity().recreate()
                            }
                        }
                    }
                    syncTheme(targetNightMode)
                }
            }
            val language = Lingver.getInstance().getLanguage()
            val languageNames = resources.getStringArray(R.array.language_names)
            languageDescTv.text = if (Lingver.getInstance().isFollowingSystemLocale()) {
                getString(R.string.follow_system)
            } else {
                when (language) {
                    Locale.SIMPLIFIED_CHINESE.language -> {
                        languageNames[POS_SIMPLIFY_CHINESE]
                    }
                    Constants.Locale.Indonesian.Language -> {
                        languageNames[POS_INDONESIA]
                    }
                    Constants.Locale.Malay.Language -> {
                        languageNames[POS_Malay]
                    }
                    else -> {
                        languageNames[POS_ENGLISH]
                    }
                }
            }
            languageRl.setOnClickListener { showLanguageAlert() }
            currentTv.text = getString(
                R.string.wallet_setting_currency_desc,
                Session.getFiatCurrency(),
                Fiats.getSymbol()
            )
            currencyRl.setOnClickListener {
                val currencyBottom = CurrencyBottomSheetDialogFragment.newInstance()
                currencyBottom.callback = object : CurrencyBottomSheetDialogFragment.Callback {
                    override fun onCurrencyClick(currency: Currency) {
                        currentTv.text = getString(
                            R.string.wallet_setting_currency_desc,
                            currency.name,
                            currency.symbol
                        )
                    }
                }
                currencyBottom.showNow(parentFragmentManager, CurrencyBottomSheetDialogFragment.TAG)
            }
        }
    }

    private fun syncTheme(isNight: Boolean) {
        binding.apply {
            val bgWindow = if (isNight) {
                R.color.bgWindowNight
            } else {
                R.color.bgWindow
            }
            val bgColor = if (isNight) {
                R.color.bgWhiteNight
            } else {
                R.color.bgWhite
            }
            val iconColor = if (isNight) {
                R.color.colorIconNight
            } else {
                R.color.colorIcon
            }
            val textPrimary = if (isNight) {
                R.color.textPrimaryNight
            } else {
                R.color.textPrimary
            }
            val textMinor = if (isNight) {
                R.color.textMinorNight
            } else {
                R.color.textMinor
            }
            root.setBackgroundResource(bgWindow)
            titleView.root.setBackgroundResource(bgColor)
            titleView.leftIb.setColorFilter(requireContext().getColor(iconColor))
            titleView.titleTv.textColorResource = textPrimary
            nightModeRl.setBackgroundResource(bgColor)
            languageRl.setBackgroundResource(bgColor)
            currencyRl.setBackgroundResource(bgColor)
            nightModeTv.textColorResource = textPrimary
            languageTv.textColorResource = textPrimary
            languageDescTv.textColorResource = textMinor
            currencyTv.textColorResource = textPrimary
            currentTv.textColorResource = textMinor

            val window = requireActivity().window
            SystemUIManager.lightUI(window, !isNight)
            SystemUIManager.setSystemUiColor(window, requireContext().getColor(bgColor))
        }
    }

    private fun showLanguageAlert() {
        val choice = resources.getStringArray(R.array.language_names)
        choice[0] = getString(R.string.follow_system)
        val selectItem = if (Lingver.getInstance().isFollowingSystemLocale()) {
            POS_FOLLOW_SYSTEM
        } else {
            when (Lingver.getInstance().getLanguage()) {
                Locale.SIMPLIFIED_CHINESE.language -> {
                    POS_SIMPLIFY_CHINESE
                }
                Constants.Locale.Indonesian.Language -> {
                    POS_INDONESIA
                }
                Constants.Locale.Malay.Language -> {
                    POS_Malay
                }
                else -> {
                    POS_ENGLISH
                }
            }
        }
        var newSelectItem = selectItem
        alertDialogBuilder()
            .setTitle(R.string.language)
            .setSingleChoiceItems(choice, selectItem) { _, which ->
                newSelectItem = which
            }
            .setPositiveButton(R.string.group_ok) { dialog, _ ->
                if (newSelectItem != selectItem) {
                    if (newSelectItem == POS_FOLLOW_SYSTEM) {
                        Lingver.getInstance().setFollowSystemLocale(requireContext())
                    } else {
                        val selectedLang = when (newSelectItem) {
                            POS_SIMPLIFY_CHINESE -> Locale.SIMPLIFIED_CHINESE.language
                            POS_INDONESIA -> Constants.Locale.Indonesian.Language
                            POS_Malay -> Constants.Locale.Malay.Language
                            else -> Locale.US.language
                        }
                        val selectedCountry = when (newSelectItem) {
                            POS_SIMPLIFY_CHINESE -> Locale.SIMPLIFIED_CHINESE.country
                            POS_INDONESIA -> Constants.Locale.Indonesian.Country
                            POS_Malay -> Constants.Locale.Malay.Country
                            else -> Locale.US.country
                        }
                        val newLocale = Locale(selectedLang, selectedCountry)
                        Lingver.getInstance().setLocale(requireContext(), newLocale)
                    }
                    TimeCache.singleton.evictAll()
                    requireActivity().onBackPressed()
                    requireActivity().recreate()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun getViewCoordinates(view: View): Coordinate {
        return Coordinate(
            getRelativeLeft(view) + view.width / 2,
            getRelativeTop(view) + view.height / 2
        )
    }

    private fun getRelativeLeft(myView: View): Int {
        return if ((myView.parent as View).id == ThemeActivity.ROOT_ID) myView.left else myView.left + getRelativeLeft(
            myView.parent as View
        )
    }

    private fun getRelativeTop(myView: View): Int {
        return if ((myView.parent as View).id == ThemeActivity.ROOT_ID) myView.top else myView.top + getRelativeTop(
            myView.parent as View
        )
    }
}
