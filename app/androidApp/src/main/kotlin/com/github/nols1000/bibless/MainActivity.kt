package com.github.nols1000.bibless

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repository = Bibless.repository(this)
        setContent {
            App(repository)
        }
    }

    override fun onStart() {
        super.onStart()
        Bibless.sync(this).start()
    }

    override fun onStop() {
        Bibless.sync(this).stop()
        super.onStop()
    }
}
