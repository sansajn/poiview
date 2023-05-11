package com.example.poiview.map

import android.graphics.Color
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource

/** Line layer with some sample data. */
class SampleLineLayer(val mapStyle: Style) {
	init {
		val GEOJSON_SOURCE_ID = "sample-line-source"
		val GEOJSON_LAYER_ID = "sample-line-layer"

		// define some data (route) to show in Prague area
		val coordinates = listOf<Point>(
			Point.fromLngLat(14.4499106, 50.1044106),  // Holesovice
			Point.fromLngLat(14.4539178, 50.0964894),  // Karlin
			Point.fromLngLat(14.4644267, 50.0847706),  // Zizkov
			Point.fromLngLat(14.4530325, 50.0748011),  // Vinohrady
			Point.fromLngLat(14.4260389, 50.1175831),  // Troja
			Point.fromLngLat(14.4452650, 50.1255628),  // Kobylisy
			Point.fromLngLat(14.4180567, 50.0833386),  // Stare Mesto
			Point.fromLngLat(14.4254381, 50.0788497),  // Nove Mesto
			Point.fromLngLat(14.4188719, 50.0636178),  // Vysehrad
			Point.fromLngLat(14.4028644, 50.0843850)   // Mala Strana
		)

		// convert to a LineString
		val routeLineString = LineString.fromLngLats(coordinates)

		val source = GeoJsonSource(GeoJsonSource.Builder(GEOJSON_SOURCE_ID)).apply {
			geometry(routeLineString)
		}

		val layer = LineLayer(GEOJSON_LAYER_ID, GEOJSON_SOURCE_ID).apply {
			lineWidth(7.5)
			lineColor(Color.LTGRAY)
		}

		with(mapStyle) {
			addSource(source)
			addLayer(layer)
		}
	}
}
