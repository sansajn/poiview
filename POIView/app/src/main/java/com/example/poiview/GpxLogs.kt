package com.example.poiview

import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import java.io.File
import kotlin.system.measureTimeMillis

/** Helps to handle GPX log trips.
 * External volume access permission required. */
class GpxLogs(private val parentFragment: Fragment) {
	/** @returns list of GPX files in `trip_logs` directory (e.g. `/storage/emulated/0/trip_logs`). */
	fun list(): ArrayList<String> {
		// figure out trip_logs path e.g. /storage/emulated/0/trip_logs
		val externalStorageDir = Environment.getExternalStorageDirectory()
		val tripLogPath = "$externalStorageDir/trip_logs"
		Log.d(TAG, "tripLogPath=$tripLogPath")

		val result = arrayListOf<String>()

		val elapsed = measureTimeMillis {
			File(tripLogPath).walk().forEach {
				if (it.isFile && it.extension == "gpx")
					result.add(it.absolutePath)
			}
		}

		Log.d(TAG, "listing trip_logs folder ($tripLogPath) (${result.size}): ${elapsed}ms")

		return result
	}

	companion object {
		const val TAG = "GpxLogs"
	}
}