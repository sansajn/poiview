package com.example.poiview.map

import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSource

/** Layer to visualize days (as linestring). */
class DayLayer(private val mapStyle: Style) {
	/** @param dayBatch collection of days as line-strings patches visible on screen (so they can represents just part of day path if not fully visible on screen) */
	fun showDay(dayBatch: List<LineString>) {
		val lightnessStep = if (dayBatch.size > 1)
			(LINE_COLOR_MAX_LIGHTNESS - LINE_COLOR_MIN_LIGHTNESS) / (dayBatch.size-1.0)
		else 0.0

		updateSource(FeatureCollection.fromFeatures(
			dayBatch.mapIndexed { idx, elem ->
				// compute color for day based on day index
				val gray = Math.min(
					LINE_COLOR_MIN_LIGHTNESS + idx*lightnessStep,
					LINE_COLOR_MAX_LIGHTNESS.toDouble()) * (255.0/100.0)

				Feature.fromGeometry((elem)).apply {
					addStringProperty("color", rgbToHtml(gray.toInt(), gray.toInt(), gray.toInt()))
				}
			}))
	}

	private fun rgbToHtml(r: Int, g: Int, b: Int): String {
		return String.format("#%02X%02X%02X", r.toByte(), g.toByte(), b.toByte())
	}

	private fun updateSource(features: FeatureCollection) {
		with(mapStyle.getSource(SOURCE_ID)!! as GeoJsonSource) {
			featureCollection(features)
		}
	}

	companion object {
		const val SOURCE_ID = "day-line-source"
		const val LAYER_ID = "day-line-layer"
		const val TAG = "DayLayer"
	}

	private val LINE_WIDTH = 3.0
	private val LINE_COLOR_MIN_LIGHTNESS = 30  // L value from HSL color space
	private val LINE_COLOR_MAX_LIGHTNESS = 70
	private val DAY_PALETTE_SIZE = 10

	/* TODO: create palette (for 10 items) by multiplying 25.5 (10% of lightness from HSV color space) to ger lighter color from 30% to 70% */

	init {
		val source = GeoJsonSource(GeoJsonSource.Builder(SOURCE_ID)).apply {
			FeatureCollection.fromFeatures(listOf<Feature>())
		}

		val layer = LineLayer(LAYER_ID, SOURCE_ID).apply {
			lineWidth(LINE_WIDTH)
			lineColor(Expression.get("color"))
		}

		with(mapStyle) {
			addSource(source)
			addLayer(layer)
		}
	}
}
