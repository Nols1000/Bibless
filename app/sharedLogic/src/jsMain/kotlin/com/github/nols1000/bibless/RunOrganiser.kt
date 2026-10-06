package com.github.nols1000.bibless

import com.github.nols1000.bibless.barcode.encodeQr
import com.github.nols1000.bibless.race.TimingMode
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.Promise

/** A run for the web app. [format] is `"TOKENS"` or `"SCAN_ORDER"`; [createdAt] is epoch milliseconds. */
@JsExport
class RunInfo(val id: String, val name: String, val date: String?, val format: String, val createdAt: Double)

/** A QR code as one SVG path over a [size] × [size] grid of modules, without the quiet zone. */
@JsExport
class QrPath(val size: Int, val path: String)

/** The organiser's side of [RunRepository] for the web app, with promises instead of suspend functions. */
@JsExport
@OptIn(DelicateCoroutinesApi::class)
class RunOrganiser internal constructor(private val repository: RunRepository) {

    fun listRuns(): Promise<Array<RunInfo>> = GlobalScope.promise { repository.runs().map { it.info() }.toTypedArray() }

    fun getRun(id: String): Promise<RunInfo?> = GlobalScope.promise { repository.run(id)?.info() }

    /** Rejects if [name] is blank or [format] unknown. */
    fun createRun(name: String, date: String?, format: String): Promise<RunInfo> = GlobalScope.promise {
        repository.create(name, date, TimingMode.valueOf(format)).info()
    }

    fun deleteRun(id: String) = repository.delete(id)

    /** The run QR code that volunteers scan to join [id], or null for an unknown run. */
    fun runQr(id: String): Promise<QrPath?> = GlobalScope.promise {
        repository.hint(id)?.let { qrPath(it.toJson()) }
    }

    companion object {
        /** An organiser whose runs and key are kept in this browser's local storage. */
        @JsStatic
        fun create() = RunOrganiser(RunRepository(LocalStorageStore()))
    }
}

internal fun qrPath(text: String): QrPath {
    val matrix = encodeQr(text)
    val path = buildString {
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) append("M$x ${y}h1v1h-1z")
        }
    }
    return QrPath(matrix.width, path)
}

private fun RunSummary.info() = RunInfo(id, name, date, format.name, createdAt.toDouble())
