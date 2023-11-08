package com.example.poiview.map

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.poiview.GpxLog
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/** Layer to show cycling activities.
 * Cycling activities comes from GPX files from `/storage/SDCARD_ID/trip_logs` directory (e.g. `/storage/12FD-0E09/trip_logs`).
 * Requirements: access to external storage (SDCARD). */
class CycleLayer(private val mapStyle: Style) {
	/** @param gpxBatch list of GPX cycling activity files to be shown. */
	fun showTrip(gpxBatch: ArrayList<String>) {
		// figure out what is new in gpxBatch
		val gpxBatchSet = gpxBatch.toSet()
		val newGpx = gpxBatchSet.subtract(_prevGpxBatchSet)
		if (newGpx.isEmpty())
			return  // there is nothing new to show

		val uiThread = Handler(Looper.getMainLooper())
		val executor = Executors.newSingleThreadExecutor()

		/* TODO: instead of executing loading new features directly by executor there
		    prepare task/job (something) runnable and share it via concurrent queue.
		    Then run executor if it is not already running. Executor will pull work
		    from the queue. This would allow us later to work only on latest update
		    requests. */

		executor.execute {  // running in a separate thread
			// TODO: we want some kind of continues result delivery there because for now result are delivered after processing all log files and this can take >5s
			val id = _loadDataJobCounter.incrementAndGet()

			// prepare new features with `path` property set to GPX file path
			val features = newGpx.map {
				var result: Feature
				val takes = measureTimeMillis {
					// in case there is something new to show, cancel the current one job
					val jobCount = _loadDataJobCounter.get()
					if (jobCount > id)
						return@map emptyFeature()

					val log = GpxLog(it)
					if (!log.isValid()) {
						Log.d(TAG, "invalid GPX file '$it'")
						return@map emptyFeature()
					}

					val route = log.route()
					if (route.coordinates().size < 2) {
						Log.d(TAG, "invalid GPX route at least two points expected")
						return@map emptyFeature()
					}

					result = Feature.fromGeometry(route).apply {
						addStringProperty("path", it)
					}
				}
				Log.d(TAG, "loading '$it': ${takes}ms")
				result
			}.filter {// filter out empty features
				it.geometry() != null
			}

			if (id >= _loadDataJobCounter.get()) {  // update only the most current job
				uiThread.post {  // notify new features ready
					notifyNewFeaturesReady(features, gpxBatchSet)
				}
			}
			else
				Log.d(TAG, "show job ($id) canceled")

			_loadDataJobCounter.decrementAndGet()
		}
	}

	/** @param newFeatures list of new features out of the gpxBatch list to shown on screen.
	 * @param gpxBatch list of GPX files to shown (used to figure out what to reuse). */
	private fun notifyNewFeaturesReady(newFeatures: List<Feature>, gpxBatch: Set<String>) {
		val commonGpx = _prevGpxBatchSet.intersect(gpxBatch)

		// find out common features
		val commonFeatures = _features.features()?.filter {
			val path = it.getStringProperty("path")
			commonGpx.contains(path)
		}

		Log.d(TAG, "${commonFeatures?.size} features reused")

		_features = with(commonFeatures?.plus(newFeatures) ?: newFeatures) {
			FeatureCollection.fromFeatures(this)
		}

		_prevGpxBatchSet = gpxBatch
		updateFeatures()
	}

	private fun updateFeatures() {
		with(mapStyle.getSource(SOURCE_ID)!! as GeoJsonSource) {
			featureCollection(
				FeatureCollection.fromFeatures(_features.features()!!))  // update with a copy of _features
		}
	}

	private fun emptyFeature(): Feature {
		return Feature.fromGeometry(null)
	}

	companion object {
		const val SOURCE_ID = "cycle-line-source"
		const val LAYER_ID = "cycle-line-layer"
		const val TAG = "CycleLayer"
	}

	private var _prevGpxBatchSet = setOf<String>()  // list of cycling activities shown by previous showTrip() call
	private var _features = FeatureCollection.fromFeatures(listOf<Feature>())  // feature collection shown in map
	private val _loadDataJobCounter = AtomicInteger(0)  // load data job counter to figure out number of parallel jobs running (the goal is to have just one load data job at time)

	init {
		val source = GeoJsonSource(GeoJsonSource.Builder(SOURCE_ID)).apply {
			featureCollection(FeatureCollection.fromFeatures(listOf<Feature>()))
		}

		val layer = LineLayer(LAYER_ID, SOURCE_ID).apply {
			lineWidth(7.5)
			lineColor(Color.BLACK)
		}

		with(mapStyle) {
			addSource(source)
			addLayer(layer)
		}
	}
}
