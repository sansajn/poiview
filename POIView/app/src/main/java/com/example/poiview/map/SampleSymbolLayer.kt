package com.example.poiview.map

import android.graphics.Bitmap
import com.mapbox.geojson.MultiPoint
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource

/** Symbol layer with some sample data shown as POIs. */
class SampleSymbolLayer(val mapStyle: Style, val poiIcon: Bitmap) {
	init {
		val GEOJSON_SOURCE_ID = "sample-symbol-source"
		val GEOJSON_LAYER_ID = "sample-symbol-layer"
		val ICON_ID = "poi-love"

		val coordinates = listOf<Point>(
			Point.fromLngLat(14.4000667, 50.1046031)   // Bubenc
		)

		val poiMultiPoint = MultiPoint.fromLngLats(coordinates)

		val source = GeoJsonSource(GeoJsonSource.Builder(GEOJSON_SOURCE_ID)).apply {
			geometry(poiMultiPoint)
		}

		val layer = SymbolLayer(GEOJSON_LAYER_ID, GEOJSON_SOURCE_ID).apply {
			iconImage(ICON_ID)
		}

		with(mapStyle) {
			addImage(ICON_ID, poiIcon)
			addSource(source)
			addLayer(layer)
		}
	}
}