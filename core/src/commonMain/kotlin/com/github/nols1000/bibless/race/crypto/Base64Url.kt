package com.github.nols1000.bibless.race.crypto

import kotlin.io.encoding.Base64

private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

internal fun ByteArray.toBase64Url(): String = base64Url.encode(this)

internal fun String.fromBase64Url(): ByteArray = base64Url.decode(this)
