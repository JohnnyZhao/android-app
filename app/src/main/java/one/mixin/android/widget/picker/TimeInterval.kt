package one.mixin.android.widget.picker

import one.mixin.android.MixinApplication
import one.mixin.android.R

val timeIntervalUnits: Array<String> by lazy {
    MixinApplication.get().resources.getStringArray(R.array.time_interval_units)
}

val numberList by lazy {
    listOf(
        (1..59).map { it.toString() }.toList(),
        (1..59).map { it.toString() }.toList(),
        (1..23).map { it.toString() }.toList(),
        (1..6).map { it.toString() }.toList(),
        (1..12).map { it.toString() }.toList()
    )
}

fun toTimeInterval(interval: Long): String = when {
    interval < 60000L -> "${interval / 1000L} ${timeIntervalUnits[0]}"
    interval < 3600000L -> "${interval / 60000L} ${timeIntervalUnits[1]}"
    interval < 86400000L -> "${interval / 3600000L} ${timeIntervalUnits[2]}"
    interval < 604800000L -> "${interval / 86400000L} ${timeIntervalUnits[3]}"
    else -> "${interval / 604800000L} ${timeIntervalUnits[4]}"
}

fun toTimeIntervalIndex(interval: Long): Pair<Int, Int> = when {
    interval < 60000L -> Pair(0, (interval / 10000L).toInt() - 1)
    interval < 3600000L -> Pair(1, (interval / 600000L).toInt() - 1)
    interval < 86400000L -> Pair(2, (interval / 3600000L).toInt() - 1)
    interval < 604800000L -> Pair(3, (interval / 86400000L).toInt() - 1)
    else -> Pair(4, (interval / 604800000L).toInt() - 1)
}
