package com.example.poiview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.example.poiview.databinding.FragmentFirstBinding
import com.example.poiview.map.GalleryLayer
import com.example.poiview.map.OnMarkerClick
import com.example.poiview.map.SampleClusterLayer
import com.example.poiview.map.SampleLineLayer
import com.example.poiview.map.SampleSymbolLayer
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mapbox.geojson.Point
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

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

		_db = DBMain(requireContext())  // database initialisation

		// icons
		_photoIcon = loadIcon(R.drawable.marker_camera)
		_poiIcon = loadIcon(R.drawable.marker_empty)
		_loveIcon = loadIcon(R.drawable.marker_love)
		_starIcon = loadIcon(R.drawable.marker_star)
		_jewelryIcon = loadIcon(R.drawable.marker_jewelry)

		binding.mapView.getMapboxMap()?.loadStyleUri(
			Style.MAPBOX_STREETS,
			object : Style.OnStyleLoaded {
				override fun onStyleLoaded(style: Style) {
					Log.d(TAG, "map loaded")
					_galleryLayer = GalleryLayer(style, _photoIcon,
						GalleryPhotoShow(_db!!, this@FirstFragment))

					val elapsedPois = measureTimeMillis {
						showPois()
					}
					Log.d(TAG, "showing POIs: ${elapsedPois}ms")

					SampleLineLayer(style)
					SampleSymbolLayer(style, _loveIcon)
					SampleClusterLayer(style, _starIcon)

					// TODO: shouldn't be permission check part of the listGalleryFolder function?
					// check for external storage (all files) permission
					if (checkPermission()) {
						Log.d(TAG, "external storage permission already granted")
						executeShowPhotoGalleryPipeline()
					}
					else {
						Log.d(TAG, "external storage permission not granted, request")
						requestPermission()
						// TODO: are we continue from there after permission granted?
					}
				}
			})

		val map = binding.mapView.getMapboxMap()
		if (map != null) {
			map.addOnMapClickListener(
				object : OnMapClickListener {
					override fun onMapClick(point: Point): Boolean {
						// TODO: get list of clicked features ...
						val screenPoint = map.pixelForCoordinate(point)

						map.queryRenderedFeatures(
							RenderedQueryGeometry(screenPoint),
							RenderedQueryOptions(listOf(GalleryLayer.LAYER_ID), null)
						) {

							if (!it.isValue || it.value!!.isEmpty())
								return@queryRenderedFeatures

							Log.d(TAG, it.value.toString())

							val selectedFeature = it.value!![0].feature
							_galleryLayer.onPoiClicked(selectedFeature)
						}

						Log.d(TAG, "map clicked ...")
						return true
					}
				}
			)
		}
	}

	private fun showPois() {
		val poiCursor = _db!!.queryPois()

		val idColIdx = poiCursor.getColumnIndex("id")
		val lonColIdx = poiCursor.getColumnIndex("lon")
		val latColIdx = poiCursor.getColumnIndex("lat")
		val nameColIdx = poiCursor.getColumnIndex("name")

		// check records
		if (poiCursor.moveToFirst()) {
			do {
				val lon = poiCursor.getDouble(lonColIdx)
				val lat = poiCursor.getDouble(latColIdx)
				val id = poiCursor.getLong(idColIdx)

				// TODO: optimize JSON structure (what way?)
				val poiData = JsonObject().apply {
					add("table", JsonPrimitive("poi"))  // TODO: we should not refer to poi table by string (this can change)
					add("id", JsonPrimitive(id))
				}

				// TODO: this should be also rewritten using layer as in a case of gallery POIs
				addPoiToMap(lon, lat, poiData, _poiIcon)
			}
			while (poiCursor.moveToNext())
		}

		poiCursor.close()
	}

	private fun showGalleryPois(pois: ArrayList<Long>) {  // TODO: can we use something more general than ArrayList?
		// TODO: we need location and id from gallery table (not path and date) so quering whole record is ineffcient
		val galleryCursor = _db!!.queryGallery(pois)

		// create list of column indices
		val lonColIdx = galleryCursor.getColumnIndex("lon")
		val latColIdx = galleryCursor.getColumnIndex("lat")
		val idColIdx = galleryCursor.getColumnIndex("id")

		// iterate records
		if (galleryCursor.moveToFirst()) {
			// TODO: do we have for each algorithm? List has .forEach, cursor probably not
			do {
				val lon = galleryCursor.getDouble(lonColIdx)
				val lat = galleryCursor.getDouble(latColIdx)
				val id = galleryCursor.getInt(idColIdx)

				val poiData = JsonObject().apply {
					add("table", JsonPrimitive("gallery"))
					add("id", JsonPrimitive(id))
				}

				_galleryLayer.addPhoto(lon, lat, poiData)

//				Log.d(TAG, "gallery item id=$id ($lat, $lon) added to map")
			}
			while (galleryCursor.moveToNext())
		}

		galleryCursor.close()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	private fun addPoiToMap(lon: Double, lat: Double, poiData: JsonObject, poiIcon: Bitmap) {
		// Create an instance of the Annotation API and get the PointAnnotationManager.
		val annotationApi = binding.mapView.annotations

		// TODO: what scope function can I use there to handle manager?
		val pointAnnotationManager = annotationApi.createPointAnnotationManager(binding.mapView)
		pointAnnotationManager.addClickListener(OnMarkerClick(_db!!, this))  // TODO: OnPointAnnotationClickListener implementation goes there

		// Set options for the resulting symbol layer.
		val pointAnnotationOptions: PointAnnotationOptions =
			PointAnnotationOptions()
				.withPoint(Point.fromLngLat(lon, lat))  // Define a geographic coordinate.
				.withIconImage(poiIcon)  // Specify the bitmap you assigned to the point annotation
				.withData(poiData)

		// Add the resulting pointAnnotation to the map.
		pointAnnotationManager.create(pointAnnotationOptions)
	}

	private fun loadIcon(@DrawableRes markerResourceId: Int): Bitmap =
		bitmapFromDrawableRes(requireContext(), markerResourceId)!!

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

	// permission stuff TODO: move it into own file
	private fun requestPermission() {
		Log.d(TAG, "requestPermission(ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)")
		with(Intent()) {
			action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
			storageActivityResultLauncher.launch(this)
		}
	}

	/** Executes pipeline showing photography gallery on map. */
	private fun executeShowPhotoGalleryPipeline() {  // TODO: not sure about function name find something bether
		val executor = Executors.newSingleThreadExecutor()

		val handler = Handler(Looper.getMainLooper())

		val photos = GalleryPhotoBatch(this, _db!!)

		executor.execute {
			var someData = false

			do {
				val photoBatch = photos.nextBatch()
				someData = photoBatch != null

				if (someData) {
					handler.post {  // we have gallery data available so notify UI thread to show it
						val elapsedGallery = measureTimeMillis {
							showGalleryPois(photoBatch!!)  // TODO: do we need a copy of photBath there?
						}
						Log.d(TAG, "showing gallery: ${elapsedGallery}ms")
					}
				}
			} while (someData)

			Log.d(TAG, "all gallery photos processed")
		}

		executor.shutdown()
	}

	// this is called after external storage access granted
	private val storageActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		if (checkPermission()) {
			Log.d(TAG, "external storage permission granted")
			executeShowPhotoGalleryPipeline()
		}
		else {
			Log.d(TAG, "external storage permission denied")
		}
	}

	private fun checkPermission(): Boolean {
		return Environment.isExternalStorageManager()
	}

	companion object {
		const val TAG = "FirstFragment"
	}

	private var _db: DBMain? = null  // TODO: any way to avoid nullable type and var
	private lateinit var _photoIcon: Bitmap
	private lateinit var _poiIcon: Bitmap
	private lateinit var _loveIcon: Bitmap
	private lateinit var _starIcon: Bitmap
	private lateinit var _jewelryIcon: Bitmap
	private lateinit var _galleryLayer: GalleryLayer
}
