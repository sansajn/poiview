package com.example.poiview.map

import android.graphics.Color
import android.util.Log
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import io.ticofab.androidgpxparser.parser.GPXParser
import io.ticofab.androidgpxparser.parser.domain.Gpx
import java.io.File
import java.io.FileInputStream

/** Layer to show trips from GPX files from `/storage/SDCARD_ID/trip_logs` (e.g. /storage/12FD-0E09/trip_logs) directory.
 * Requirements: access to external storage (SDCARD). */
class TripLayer(private val mapStyle: Style) {
	fun addTrip(gpxFile: String) {
		// TODO: implement

		Log.d(TAG, "loading $gpxFile ...")

		val parser = GPXParser()
		val gpx: Gpx? = parser.parse(FileInputStream(File(gpxFile)))

		// TODO: only the first track and segment is taken account
		val route: LineString? = gpx?.let {
			Log.d(TAG, "  tracks:")
			it.tracks.forEach {track ->
				Log.d(TAG, "    ${track.trackName}")
				track.trackSegments.forEach { segment ->
					return@let LineString.fromLngLats(
						segment.trackPoints.map { pt ->
							Point.fromLngLat(pt.longitude, pt.latitude)
					})
				}
			}
			null
		}

		if (route != null) {
			Log.d(TAG, "found route")
			val routes = _tripCollection.features()!!.toMutableList()
			routes.add(Feature.fromGeometry(route))

			_tripCollection = FeatureCollection.fromFeatures(routes)

			with(mapStyle.getSource(SOURCE_ID)!! as GeoJsonSource) {
				featureCollection(_tripCollection)
			}
		}
	}

	companion object {
		const val SOURCE_ID = "trip-line-source"
		const val LAYER_ID = "trip-line-layer"
		const val TAG = "TripLayer"
	}

	private var _tripCollection: FeatureCollection

	init {
		_tripCollection = FeatureCollection.fromFeatures(listOf<Feature>())

		val source = GeoJsonSource(GeoJsonSource.Builder(SOURCE_ID)).apply {
			featureCollection(_tripCollection)
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
