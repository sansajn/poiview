package com.example.poiview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.example.poiview.databinding.FragmentFirstBinding
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager

val praguePos = Point.fromLngLat(14.434564115508454, 50.08353884895491)

/**
 * Fragment to show POIs on mapview.
 */
class FirstFragment : Fragment() {

	private var _binding: FragmentFirstBinding? = null

	// This property is only valid between onCreateView and
	// onDestroyView.
	private val binding get() = _binding!!

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View? {

		_binding = FragmentFirstBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		mapView = view.findViewById(R.id.mapView)
		mapView?.getMapboxMap()?.loadStyleUri(
			Style.MAPBOX_STREETS,
			object : Style.OnStyleLoaded {
				override fun onStyleLoaded(style: Style) {
					Log.d("marker", "map loaded")
					//showPois()
					showGalleryPois()
				}
			})
	}

	private fun showPois() {
		val db = DBMain(requireContext())
		val poiCursor = db.queryPois()

		val idColIdx = poiCursor.getColumnIndex("id")
		val lonColIdx = poiCursor.getColumnIndex("lon")
		val latColIdx = poiCursor.getColumnIndex("lat")
		val nameColIdx = poiCursor.getColumnIndex("name")

		// check records
		if (poiCursor.moveToFirst()) {
			do {
				val lon = poiCursor.getDouble(lonColIdx)
				val lat = poiCursor.getDouble(latColIdx)
				addAnotationToMap(lon, lat, R.drawable.red_marker)
			}
			while (poiCursor.moveToNext())
		}
	}

	private fun showGalleryPois() {
		val db = DBMain(requireContext())
		val galleryCursor = db.queryGallery()

		// create list of column indices
		val lonColIdx = galleryCursor.getColumnIndex("lon")
		val latColIdx = galleryCursor.getColumnIndex("lat")

		// iterate records
		if (galleryCursor.moveToFirst()) {
			// TODO: do we have for each algorithm? List has .forEach, cursor probably not
			do {
				val lon = galleryCursor.getDouble(lonColIdx)
				val lat = galleryCursor.getDouble(latColIdx)
				addAnotationToMap(lon, lat, R.drawable.map_pin)
				Log.d("poiview", "gallery item (${lat}, ${lon}) added")
			}
			while (galleryCursor.moveToNext())
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	// TODO: rename to something else e.g. insertPoiToMap
	private fun addAnotationToMap(lon: Double, lat: Double, @DrawableRes markerResourceId: Int) {
		// Create an instance of the Annotation API and get the PointAnnotationManager.
		bitmapFromDrawableRes(requireContext(), markerResourceId)?.let {
			val annotationApi = mapView?.annotations
			val pointAnnotationManager = annotationApi?.createPointAnnotationManager(mapView!!)

			// Set options for the resulting symbol layer.
			val pointAnnotationOptions: PointAnnotationOptions =
				PointAnnotationOptions()
					.withPoint(Point.fromLngLat(lon, lat))  // Define a geographic coordinate.
					.withIconImage(it)  // Specify the bitmap you assigned to the point annotation

			// Add the resulting pointAnnotation to the map.
			pointAnnotationManager?.create(pointAnnotationOptions)
		}
	}

	private fun bitmapFromDrawableRes(context: Context, @DrawableRes resourceId: Int) = convertDrawableToBitmap(
		AppCompatResources.getDrawable(context, resourceId))

	private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
		if (sourceDrawable == null)
			return null

		return if (sourceDrawable is BitmapDrawable) {
			sourceDrawable.bitmap
		} else {
			// copying drawable object to not manipulate on the same reference
			val constantState = sourceDrawable.constantState ?: return null
			val drawable = constantState.newDrawable().mutate()
			val bitmap: Bitmap = Bitmap.createBitmap(
				drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
			val canvas = Canvas(bitmap)
			drawable.setBounds(0, 0, canvas.width, canvas.height)
			drawable.draw(canvas)
			bitmap
		}
	}

	private var mapView: MapView? = null  // TODO: can we use lateinit there and avoid null type?
}