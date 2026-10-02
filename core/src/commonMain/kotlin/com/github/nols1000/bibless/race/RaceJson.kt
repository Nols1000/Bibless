package com.github.nols1000.bibless.race

import com.github.nols1000.bibless.race.crypto.sha256
import com.github.nols1000.bibless.race.crypto.toBase64Url
import kotlinx.serialization.json.Json

/** Readers ignore unknown fields, so that older apps can read data written by newer ones. */
internal val raceJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    classDiscriminator = "type"
}

/** The ID of signed content: base64url SHA-256 of its exact JSON bytes. */
internal suspend fun hashOf(json: String): String = sha256(json.encodeToByteArray()).toBase64Url()
