package com.example.poiview

import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import com.example.poiview.map.TripLayer
import java.io.File
import kotlin.system.measureTimeMillis

/** Helps to handle GPX log trips.
 * SD card access permission required. */
class GpxTrips(private val parentFragment: Fragment) {
	fun show(layer: TripLayer) {
		val trips = listTripLogs()

		trips.forEach { Log.d(TAG, "$it") }

		trips.forEach {
			layer.addTrip(it)
		}
	}

	// TODO: this will not work on devices without SD Card!! Maybe I should move trip_logs to internal storage.
	/** @returns list of GPX files in trip_logs directory (e.g. e.g. /storage/9C33-6BBD/trip_logs). */
	private fun listTripLogs(): ArrayList<String> {
		// TODO: move code to the module and reus the module there and in GalleryPhotoBatch to access SDCard folders
		// figure out trip_logs path e.g. /storage/9C33-6BBD/trip_logs

		val externalVolumes = MediaStore.getExternalVolumeNames(parentFragment.requireContext())
		val sdCardVolumes = externalVolumes.filter {
			it != MediaStore.VOLUME_EXTERNAL_PRIMARY && it != MediaStore.VOLUME_EXTERNAL
		}
		if (sdCardVolumes.isEmpty())  // no SD Card
			return arrayListOf<String>()

		// TODO: for now just take the firs one SD Card in system
		val sdCardTripLogPath = "/storage/${sdCardVolumes[0].uppercase()}/trip_logs"
		Log.d(FirstFragment.TAG, "sdCardTripLogPath=$sdCardTripLogPath")

		val result = arrayListOf<String>()

		val elapsed = measureTimeMillis {
			// TODO: this seems to be work for map, not forEach
			File(sdCardTripLogPath).walk().forEach {
				if (it.isFile && it.extension == "gpx")
					result.add(it.absolutePath)
			}
		}

		Log.d(TAG, "listing SD Card trip_logs folder ($sdCardTripLogPath): ${elapsed}ms")

		return result
	}

	companion object {
		const val TAG = "GpxTrips"
	}
}