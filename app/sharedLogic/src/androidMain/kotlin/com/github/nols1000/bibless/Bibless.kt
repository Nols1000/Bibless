package com.github.nols1000.bibless

import android.content.Context
import android.content.pm.PackageManager

/** Process-wide wiring so activities and [BarcodeListenerService] share one repository, and one timer. */
object Bibless {
    @Volatile
    private var instance: Pair<BarcodeRepository, DataLayerSync>? = null

    fun repository(context: Context): BarcodeRepository = wiring(context).first

    fun sync(context: Context): DataLayerSync = wiring(context).second

    @Volatile
    private var timer: TimerRepository? = null

    /** The phone stopwatch; one per process, so a running session isn't loaded twice. */
    fun timer(context: Context): TimerRepository = timer ?: synchronized(this) {
        timer ?: context.applicationContext.let { TimerRepository(SharedPreferencesStore(it, "bibless.timer", durable = true), AndroidRaceClock(it)) }
            .also { timer = it }
    }

    private fun wiring(context: Context) = instance ?: synchronized(this) {
        instance ?: run {
            val app = context.applicationContext
            val isWatch = app.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
            val repository = BarcodeRepository(SharedPreferencesStore(app), if (isWatch) Device.WATCH else Device.PHONE)
            val sync = DataLayerSync(app, repository)
            repository.sync = sync
            (repository to sync).also { instance = it }
        }
    }
}
