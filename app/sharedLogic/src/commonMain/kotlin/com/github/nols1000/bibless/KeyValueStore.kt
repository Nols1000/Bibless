package com.github.nols1000.bibless

/** Minimal persistent string storage, implemented per platform. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}
