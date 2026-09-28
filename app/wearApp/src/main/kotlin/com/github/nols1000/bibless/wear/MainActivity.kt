package com.github.nols1000.bibless.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.github.nols1000.bibless.Bibless

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = Bibless.repository(this)
        setContent {
            WearApp(repository)
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
