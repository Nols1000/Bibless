package com.github.nols1000.bibless

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

actual fun formatRunDate(date: String): String = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(date)!!
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(parsed)
}.getOrDefault(date)
