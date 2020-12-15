package one.mixin.android.widget.linktext

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.mixin.android.extension.tapVibrate
import one.mixin.android.util.markdown.MarkwonUtil.Companion.getSimpleMarkwon
import java.util.LinkedList
import java.util.regex.Pattern

open class AutoLinkTextView(context: Context, attrs: AttributeSet?) :
    AppCompatTextView(context, attrs) {

    private var coroutineScope: CoroutineScope? = null
    private var autoLinkOnClickListener: ((AutoLinkMode, String) -> Unit)? = null
    private var autoLinkOnLongClickListener: ((AutoLinkMode, String) -> Unit)? = null

    private var autoLinkModes: Array<out AutoLinkMode>? = null

    private var customRegex: String? = null

    private var isUnderLineEnabled = false

    private var mentionModeColor = DEFAULT_COLOR
    private var hashtagModeColor = DEFAULT_COLOR
    private var urlModeColor = DEFAULT_COLOR
    private var phoneModeColor = DEFAULT_COLOR
    private var emailModeColor = DEFAULT_COLOR
    private var customModeColor = DEFAULT_COLOR
    private var defaultSelectedColor = Color.LTGRAY
    var clickTime: Long = 0

    override fun setText(text: CharSequence, type: BufferType) {
        if (TextUtils.isEmpty(text)) {
            super.setText(text, type)
            return
        }

        val spannableString = makeSpannableString(getSimpleMarkwon(context).toMarkdown(text.toString()))
        if (movementMethod == null) {
            movementMethod = LinkTouchMovementMethod()
        }
        super.setText(spannableString as CharSequence, type)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        coroutineScope = MainScope()
    }
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope?.cancel()
    }

    override fun performLongClick(): Boolean {
        return if (handleLongClick) {
            handleLongClick = false
            true
        } else {
            super.performLongClick()
        }
    }

    private var handleLongClick = false

    private fun makeSpannableString(
        text: CharSequence
    ): SpannableString {
        val spannableString = if (text is SpannableString) {
            text
        } else {
            SpannableString(text)
        }

        val autoLinkItems = matchedRanges(text)

        for (autoLinkItem in autoLinkItems) {
            val currentColor = getColorByMode(autoLinkItem.autoLinkMode)

            val clickableSpan = if (autoLinkItem.autoLinkMode == AutoLinkMode.MODE_URL) {
                object : LongTouchableSpan(currentColor, defaultSelectedColor, isUnderLineEnabled) {
                    var job: Job? = null
                    override fun onClick(widget: View) {
                        if (isLongPressed) return
                        autoLinkOnClickListener?.let {
                            it(autoLinkItem.autoLinkMode, autoLinkItem.matchedText)
                        }
                    }

                    override fun startLongClick() {
                        job = coroutineScope?.launch {
                            setLongPressed(false)
                            handleLongClick = false
                            delay(LONG_CLICK_TIME)
                            autoLinkOnLongClickListener?.let {
                                context.tapVibrate()
                                it(autoLinkItem.autoLinkMode, autoLinkItem.matchedText)
                            }
                            setLongPressed(true)
                            handleLongClick = true
                        }
                    }

                    override fun cancelLongClick(): Boolean {
                        return if (isLongPressed) {
                            false
                        } else {
                            if (job?.isActive == true) {
                                job?.cancel()
                                setLongPressed(false)
                                handleLongClick = false
                            }
                            true
                        }
                    }

                    override fun updateDrawState(textPaint: TextPaint) {
                        super.updateDrawState(textPaint)
                        val textColor = if (isPressed) pressedTextColor else normalTextColor
                        textPaint.color = textColor
                        textPaint.bgColor = Color.TRANSPARENT
                        textPaint.isUnderlineText = isUnderLineEnabled
                    }
                }
            } else {
                object : TouchableSpan(currentColor, defaultSelectedColor, isUnderLineEnabled) {
                    override fun onClick(widget: View) {
                        autoLinkOnClickListener?.let {
                            it(autoLinkItem.autoLinkMode, autoLinkItem.matchedText)
                        }
                    }

                    override fun updateDrawState(textPaint: TextPaint) {
                        super.updateDrawState(textPaint)
                        val textColor = if (isPressed) pressedTextColor else normalTextColor
                        textPaint.color = textColor
                        textPaint.bgColor = Color.TRANSPARENT
                        textPaint.isUnderlineText = isUnderLineEnabled
                    }
                }
            }

            spannableString.setSpan(
                clickableSpan,
                autoLinkItem.startPoint,
                autoLinkItem.endPoint,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannableString
    }

    private fun matchedRanges(text: CharSequence): List<AutoLinkItem> {

        val autoLinkItems = LinkedList<AutoLinkItem>()

        if (autoLinkModes == null) {
            throw NullPointerException("Please add at least one mode")
        }

        for (anAutoLinkMode in autoLinkModes!!) {
            val regex = Utils.getRegexByAutoLinkMode(anAutoLinkMode, customRegex)
            val pattern = Pattern.compile(regex)
            val matcher = pattern.matcher(text)

            if (anAutoLinkMode == AutoLinkMode.MODE_PHONE) {
                while (matcher.find()) {
                    if (matcher.group().length > MIN_PHONE_NUMBER_LENGTH)
                        autoLinkItems.add(
                            AutoLinkItem(
                                matcher.start(),
                                matcher.end(),
                                matcher.group(),
                                anAutoLinkMode
                            )
                        )
                }
            } else {
                while (matcher.find()) {
                    autoLinkItems.add(
                        AutoLinkItem(
                            matcher.start(),
                            matcher.end(),
                            matcher.group(),
                            anAutoLinkMode
                        )
                    )
                }
            }
        }

        return autoLinkItems
    }

    private fun getColorByMode(autoLinkMode: AutoLinkMode): Int {
        return when (autoLinkMode) {
            AutoLinkMode.MODE_HASHTAG -> hashtagModeColor
            AutoLinkMode.MODE_MENTION -> mentionModeColor
            AutoLinkMode.MODE_URL -> urlModeColor
            AutoLinkMode.MODE_PHONE -> phoneModeColor
            AutoLinkMode.MODE_EMAIL -> emailModeColor
            AutoLinkMode.MODE_CUSTOM -> customModeColor
        }
    }

    fun setMentionModeColor(@ColorInt mentionModeColor: Int) {
        this.mentionModeColor = mentionModeColor
    }

    fun setHashtagModeColor(@ColorInt hashtagModeColor: Int) {
        this.hashtagModeColor = hashtagModeColor
    }

    fun setUrlModeColor(@ColorInt urlModeColor: Int) {
        this.urlModeColor = urlModeColor
    }

    fun setPhoneModeColor(@ColorInt phoneModeColor: Int) {
        this.phoneModeColor = phoneModeColor
    }

    fun setEmailModeColor(@ColorInt emailModeColor: Int) {
        this.emailModeColor = emailModeColor
    }

    fun setCustomModeColor(@ColorInt customModeColor: Int) {
        this.customModeColor = customModeColor
    }

    fun setSelectedStateColor(@ColorInt defaultSelectedColor: Int) {
        this.defaultSelectedColor = defaultSelectedColor
    }

    fun addAutoLinkMode(vararg autoLinkModes: AutoLinkMode) {
        this.autoLinkModes = autoLinkModes
    }

    fun setCustomRegex(regex: String) {
        this.customRegex = regex
    }

    fun setAutoLinkOnClickListener(autoLinkOnClickListener: (AutoLinkMode, String) -> Unit) {
        this.autoLinkOnClickListener = autoLinkOnClickListener
    }

    fun setAutoLinkOnLongClickListener(autoLinkOnLongClickListener: (AutoLinkMode, String) -> Unit) {
        this.autoLinkOnLongClickListener = autoLinkOnLongClickListener
    }

    fun enableUnderLine() {
        isUnderLineEnabled = true
    }

    companion object {

        internal val TAG = AutoLinkTextView::class.java.simpleName

        private const val MIN_PHONE_NUMBER_LENGTH = 8

        private const val DEFAULT_COLOR = Color.RED

        private const val LONG_CLICK_TIME = 200L
    }
}
