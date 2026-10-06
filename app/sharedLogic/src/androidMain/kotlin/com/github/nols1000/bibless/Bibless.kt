package com.github.nols1000.bibless

import android.content.Context
import android.content.pm.PackageManager

/** Process-wide wiring so activities and [BarcodeListenerService] share one repository, and one timer and scanner. */
object Bibless {
    @Volatile
    private var instance: Pair<BarcodeRepository, DataLayerSync>? = null

    fun repository(context: Context): BarcodeRepository = wiring(context).first

    fun sync(context: Context): DataLayerSync = wiring(context).second

    @Volatile
    private var race: Pair<TimerRepository, ScannerRepository>? = null

    /** The phone stopwatch; one per process, so a running session isn't loaded twice. */
    fun timer(context: Context): TimerRepository = raceWiring(context).first

    /** The finish scanner, signing with the same device key as [timer]. */
    fun scanner(context: Context): ScannerRepository = raceWiring(context).second

    private fun raceWiring(context: Context) = race ?: synchronized(this) {
        race ?: context.applicationContext.let {
            val device = RaceDevice(SharedPreferencesStore(it, "bibless.race", durable = true), AndroidRaceClock(it))
            (TimerRepository(device) to ScannerRepository(device)).also { race = it }
        }
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
