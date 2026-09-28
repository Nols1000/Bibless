package com.github.nols1000.bibless

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform