package com.example.poiview.map

import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.poiview.GalleryPhotoShow
import com.google.gson.JsonObject
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.MultiPoint
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import java.io.File

/** Map layer for gallery photos. */
class GalleryLayer(private val mapStyle: Style, private val poiIcon: Bitmap,
	private val show: GalleryPhotoShow) {

	private var _poiCollection: FeatureCollection

	init {
		_poiCollection = FeatureCollection.fromFeatures(listOf<Feature>())

		val source = GeoJsonSource(GeoJsonSource.Builder(SOURCE_ID)).apply {
			featureCollection(_poiCollection)
		}

		val layer = SymbolLayer(LAYER_ID, SOURCE_ID).apply {
			iconImage(ICON_ID)
		}

		with(mapStyle) {
			addImage(ICON_ID, poiIcon)
			addSource(source)
			addLayer(layer)
		}
	}

	// TODO: work with list of photos to improve efficiency (we always need to rebuild collection)
	// TODO: replace poiData with something more specific to photos
	// TODO: maybe we can use https://developer.android.com/reference/java/util/concurrent/CopyOnWriteArrayList there
	/** Add gallery photo to layer.
	@param poiData JSON with table: String, id: Integer properties. */
	fun addPhoto(lon: Double, lat: Double, poiData: JsonObject) {
		// I didn't found any other way than rebuild feature-collection, not sure how time consuming it is
		val poiList = _poiCollection.features()!!.toMutableList()

		val poiFeature = Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
			addProperty("data", poiData)
		}

		poiList.add(poiFeature)

		_poiCollection = FeatureCollection.fromFeatures(poiList)

		val source = mapStyle.getSource(SOURCE_ID)!! as GeoJsonSource
		source.featureCollection(_poiCollection)

//		Log.d(TAG, _features.toString())
	}

	/** Called when POI clicked on map. */
	fun onPoiClicked(poi: Feature) {
		// Feature{type=Feature, bbox=null, id=null, geometry=Point{type=Point, bbox=null, coordinates=[14.362478256225586, 49.981971446707774]}, properties={"data":{"id":32,"table":"gallery"}}}
		val dataJson = poi.getProperty("data")!!.asJsonObject
		val table = dataJson.get("table").asString!!  // TODO: table seems to be redundant there!
		val photoId = dataJson.get("id").asLong!!
		Log.d(TAG, "POI (table=$table, id=$photoId) clicked")

		if (table == "gallery")
			show(photoId)
	}

	companion object {
		const val SOURCE_ID = "gallery-photo-source"
		const val LAYER_ID = "gallery-photo-layer"
		const val ICON_ID = "poi-photo"
		const val TAG = "GalleryLayer"
	}

	val samplePois = listOf<Point>(
		Point.fromLngLat(14.2668019, 50.0750922)   // Hostivice-Sadová
	)
}