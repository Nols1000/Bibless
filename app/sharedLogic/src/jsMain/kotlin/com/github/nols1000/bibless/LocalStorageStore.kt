package com.github.nols1000.bibless

import web.storage.Storage
import web.storage.localStorage

/** [KeyValueStore] backed by the browser's local storage, which keeps data per site. */
class LocalStorageStore(private val storage: Storage = localStorage) : KeyValueStore {
    override fun get(key: String): String? = storage.getItem(PREFIX + key)

    override fun put(key: String, value: String) {
        if (value.isEmpty()) storage.removeItem(PREFIX + key) else storage.setItem(PREFIX + key, value)
    }

    private companion object {
        const val PREFIX = "bibless."
    }
}
