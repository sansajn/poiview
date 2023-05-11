package com.example.poiview.map

import android.graphics.Bitmap
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.MultiPoint
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource

/** Sample (symbol) layer with clustering enabled with some initial data. */
class SampleClusterLayer(val mapStyle: Style, val poiIcon: Bitmap) {
	init {
		val GEOJSON_SOURCE_ID = "sample-cluster-source"
		val GEOJSON_LAYER_ID = "sample-cluster-layer"
		val ICON_ID = "poi-star"

		val shelterList = listOf<Point>(
			Point.fromLngLat(14.5430278, 50.0457383),  // Přístřešek Hostivařský lesopark
			Point.fromLngLat(14.5406867, 50.0447242),  // Hájkův altán
			Point.fromLngLat(14.5343925, 50.0453647),  // Přístřešek hradiště Hostivař
			Point.fromLngLat(14.5402267, 50.0394139),  // Přístřešek Hostivařská přehrada
			Point.fromLngLat(14.5319997, 50.0401144),  // Turistický přístřešek K Jezeru
			Point.fromLngLat(14.5278253, 50.0406781),  // Dřevěný altán Lesopark Háje
			Point.fromLngLat(14.5291608, 50.0418639)   // Přístřešek
		)

		// TODO: use scope functions there
		val features = shelterList.map {Feature.fromGeometry(it)}
		val shelterListCollection = FeatureCollection.fromFeatures(features)

		val source = GeoJsonSource(GeoJsonSource.Builder(GEOJSON_SOURCE_ID)).apply {
			featureCollection(shelterListCollection)
		}

		// note we do not need to set anything special there, clustering is enabled by default
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