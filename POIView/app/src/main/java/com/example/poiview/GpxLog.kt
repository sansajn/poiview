package com.example.poiview

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CoordinateBounds
import com.mapbox.turf.TurfMeasurement.bbox
import io.ticofab.androidgpxparser.parser.GPXParser
import io.ticofab.androidgpxparser.parser.domain.Gpx
import java.io.File
import java.io.FileInputStream

/** GPX Log file support.
 * Note: GpxLog instances are not meant to be stored, instead use GpXLog to parse GPX data. */
class GpxLog(val path: String) {
	/** @returns activity name (taken from `gpx/trk[0]/name` property) or empty string otherwise. */
	fun name(): String {
		return if (_gpx != null) {
			val trackName = with(_gpx) {
				// TODO: only the first track and segment is taken into an account
				tracks.forEach { track ->
					return@with track.trackName
				}

				""
			}

			trackName
		}
		else ""
	}

	/** @returns activity date-time (taken from `gpx/metadata/time`) as posix timestamp or 0 otherwise. */
	fun dateTime(): Long {  // TODO: do not like the name of the function
		return if (_gpx != null) {
			val timestamp = with(_gpx) {
				metadata.time.millis / 1000L
			}
			timestamp
		}
		else 0
	}

	/** @returns route as LineString, empty LineString otherwise. */
	fun route(): LineString {
		return _route
	}

	/** @returns axis aligned route geo bounding box. */
	fun bounds(): CoordinateBounds {
		return _bounds
	}

	fun isValid(): Boolean {
		return _gpx != null // && route has at least two points
	}

	private fun emptyLineString(): LineString {
		return LineString.fromLngLats(listOf<Point>())
	}

	private fun parseRoute(): LineString {
		return if (_gpx != null) {
			val route = with(_gpx) {
				// TODO: only the first track and segment is taken into an account
				tracks.forEach { track ->
					track.trackSegments.forEach { segment ->
						return@with LineString.fromLngLats(
							segment.trackPoints.map { pt ->
								Point.fromLngLat(pt.longitude, pt.latitude)
							})
					}
				}

				emptyLineString()// in case there is nothing in GPX file
			}

			route
		}
		else emptyLineString()
	}

	private val _gpx: Gpx? = try {
		val parser = GPXParser()
		parser.parse(FileInputStream(File(path)))
	} catch (e: java.io.FileNotFoundException) {
		null
	}

	private val _route by lazy {
		parseRoute()
	}

	private val _bounds: CoordinateBounds by lazy {
		bbox(_route).let {
			assert(it.size == 4) {"4 coordinates expected aso bounding box"}
			CoordinateBounds(
				Point.fromLngLat(it[0], it[1]),
				Point.fromLngLat(it[2], it[3]))
		}
	}
}