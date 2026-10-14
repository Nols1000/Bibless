package com.github.nols1000.bibless

import android.content.Context

/**
 * [KeyValueStore] in the shared preferences file [name]. [durable] stores write to disk before
 * `put` returns, so the value survives the process being killed right after.
 */
class SharedPreferencesStore(context: Context, name: String = "bibless", private val durable: Boolean = false) : KeyValueStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        val editor = prefs.edit().putString(key, value)
        if (durable) editor.commit() else editor.apply()
    }
}
