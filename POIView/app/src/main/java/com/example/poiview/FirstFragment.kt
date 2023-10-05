package com.example.poiview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.get
import com.example.poiview.databinding.FragmentFirstBinding
import com.example.poiview.db.MainDb
import com.example.poiview.gallery.PhotoBatch
import com.example.poiview.gallery.ShowPhoto
import com.example.poiview.map.GalleryLayer
import com.example.poiview.map.OnMarkerClick
import com.example.poiview.map.CycleLayer
import com.example.poiview.map.LocationLayer
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.CoordinateBounds
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.location
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

		_db = MainDb(requireContext())  // database initialisation

		// icons
		_photoIcon = loadIcon(R.drawable.marker_camera)
		_poiIcon = loadIcon(R.drawable.marker_empty)
		_loveIcon = loadIcon(R.drawable.marker_love)
		_starIcon = loadIcon(R.drawable.marker_star)
		_jewelryIcon = loadIcon(R.drawable.marker_jewelry)

		val map = binding.mapView.getMapboxMap()

		map.loadStyleUri(
			Style.MAPBOX_STREETS,
			object : Style.OnStyleLoaded {
				override fun onStyleLoaded(style: Style) {
					Log.d(TAG, "map loaded")
					_galleryLayer = GalleryLayer(style, _photoIcon,
						ShowPhoto(_db!!, this@FirstFragment)
					)

					val elapsedPois = measureTimeMillis {
						showPois()
					}
					Log.d(TAG, "showing POIs: ${elapsedPois}ms")

					_tripLayer = CycleLayer(style)

//					SampleLineLayer(style)
//					SampleSymbolLayer(style, _loveIcon)
//					SampleClusterLayer(style, _starIcon)

					_locationLayer = LocationLayer(binding.mapView.location) {
						val mapView = binding.mapView
						if (!_haveLocation) {  // center only for the first update
							_haveLocation = true
							mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(it).build())
						}
					}

					_permissions.requestSdCardPermissionsFor(this@FirstFragment) {
						updateMap()

						// run scanner services
						executePhotoGalleryScan()
						executeCycleActivityScan()
					}

					_permissions.requestLocationPermissionsFor(this@FirstFragment) {
						_locationLayer.initFor(this@FirstFragment)
					}

					Log.d(TAG, "onStyleLoaded() done")

					mapReady()
				}
			})

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

	private fun mapReady() {
		with (binding.mapView.getMapboxMap()) {
			val mapUpdateHandler = Handler(Looper.getMainLooper())
			val updateMapJob = Runnable {
				updateMap()
			}

			addOnCameraChangeListener { _ ->
				// we do not want to trigger update directly, instead update with 1/2s period at maximum
				if (!mapUpdateHandler.hasCallbacks(updateMapJob)) {
					mapUpdateHandler.postDelayed(updateMapJob, 500)
				}
			}
		}
	}

	/** @returns visible area geo-rectangle. */
	private fun getVisibleAreaBounds(map: MapboxMap): CoordinateBounds {
		val camOpts = CameraOptions.Builder()
			.zoom(map.cameraState.zoom)
			.center(map.cameraState.center)
			.build()
		return map.coordinateBoundsForCamera(camOpts)
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

	// TODO: create dedicated class/type to handle showing gallery pois ...
	private fun showGalleryPois(pois: ArrayList<Long>) {  // TODO: can we use something more general than ArrayList?
		// TODO: we need location and id from gallery table (not path and date) so quering whole record is ineffcient
		val galleryCursor = _db!!.queryGallery(pois)

		// create list of column indices
		val lonColIdx = galleryCursor.getColumnIndex("lon")
		val latColIdx = galleryCursor.getColumnIndex("lat")
		val idColIdx = galleryCursor.getColumnIndex("id")

		// iterate records
		if (galleryCursor.moveToFirst()) {
			val photos = ArrayList<GalleryLayer.GalleryPoi>()

			do {
				// skip records without location
				if (galleryCursor.isNull(lonColIdx) || galleryCursor.isNull(latColIdx))
					continue

				val lon = galleryCursor.getDouble(lonColIdx)  // TODO: what is returned for NULL record values
				val lat = galleryCursor.getDouble(latColIdx)
				val id = galleryCursor.getInt(idColIdx)

				val poiData = JsonObject().apply {
					add("table", JsonPrimitive("gallery"))
					add("id", JsonPrimitive(id))
				}

				photos.add(GalleryLayer.GalleryPoi(lon, lat, poiData))

//				Log.d(TAG, "gallery item id=$id ($lat, $lon) added to map")
			}
			while (galleryCursor.moveToNext())

			_galleryLayer.showPhoto(photos)
		}

		galleryCursor.close()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		// TODO: we should remove all map listeners there e.g. `mapboxMap.removeOnCameraChangeListener(listener)`
		// TODO: is _binding still valid there?
		_locationLayer.onDestroy()
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

	// TODO: can we move following into gallery package as GalleryScanner?
	private fun executePhotoGalleryScan() {  // TODO: not sure about function name find something better
		Log.i(TAG, "running gallery photo scanner ...")

		val beforeScanPhotoCount = _db!!.galleryCount()

		val executor = Executors.newSingleThreadExecutor()

		// TODO: there we need list of photo paths
		val photos = PhotoBatch(this, _db!!)

		executor.execute {  // running on separate thread
			val takes = measureTimeMillis {
				// TODO: nextBatch() do not need to return anything ...
				// just scan whole library folder
				while (photos.nextBatch() != null)
					;
			}

			// show some gallery table stats
			val photoCount = _db!!.galleryCount()
			val photoWithLocationCount = _db!!.galleryWithLocationCount()
			Log.i(TAG, "gallery: found ${photoCount - beforeScanPhotoCount} new photos")
			Log.i(TAG, "gallery: photos=$photoCount, photos-with-location=$photoWithLocationCount")
			Log.i(TAG, "all gallery photos ($photoCount) processed: ${takes}ms")
		}

		executor.shutdown()
	}

	// TODO: move to CycleActivityInfo module similar as with PhotoInfo
	data class CycleActivityInfo(val title: String, val date: Long, val bounds: CoordinateBounds)

	private fun cycleActivityInfo(gpxFile: String): CycleActivityInfo {
		val log = GpxLog(gpxFile)
		val activityTitle = log.name()
		val bounds = log.bounds()
		val posixDateTime = log.dateTime()

		Log.d(TAG, "parsing '$gpxFile': '$activityTitle', $bounds, $posixDateTime ")

		return CycleActivityInfo(activityTitle, posixDateTime, bounds)
	}

	// TODO: can we move following into scanner package?
	private fun executeCycleActivityScan() {
		Log.i(TAG, "running cycling activities scanner ...")

		val beforeScanCycleCount = _db!!.cycleCount()

		val executor = Executors.newSingleThreadExecutor()

		val activities = GpxLogs(this)

		executor.execute {  // running on separate thread
			// get list of cycle activity log files
			val cycleActivities = activities.list().filter {it.contains("Cycling") || it.contains("cycling")}

			val takes = measureTimeMillis {
				// process file by file
				cycleActivities.filter {
					!_db!!.inCycleActivities(it)
				}.forEach {
					val cycleInfo = cycleActivityInfo(it)
					val minPos = cycleInfo.bounds.southwest
					val maxPos = cycleInfo.bounds.northeast
					_db!!.addToCycle(
						MainDb.CycleRecord(
							cycleInfo.title, cycleInfo.date, it,
							minPos.longitude(), minPos.latitude(), maxPos.longitude(), maxPos.latitude()
						)
					)
				}
			}

			val cycleCount = _db!!.cycleCount()
			Log.i(TAG, "cycle: found ${cycleCount - beforeScanCycleCount} new cycling activities")
			Log.i(TAG, "all cycling activities ($cycleCount) processed: ${takes}ms")
		}

		executor.shutdown()
	}

	private fun updateMap() {
		run {
			var poiCount = 0
			val takes = measureTimeMillis {
				poiCount = showVisibleGalleryPois()
			}
			Log.d(TAG, "rendering visible gallery pois ($poiCount) takes: ${takes}ms")
		}

		run {
			var cycleCount = 0
			val takes = measureTimeMillis {
				cycleCount = showVisibleCycleActivity()
			}
			Log.d(TAG, "rendering visible cycling activities ($cycleCount) takes: ${takes}ms")
		}
	}

	/** Shows visible gallery-pois.
	 * @returns number of visible POIs (taken from gallery table) */
	private fun showVisibleGalleryPois(): Int {
		val map = binding.mapView.getMapboxMap()

		val visiblePoiIds = with(_db!!.queryGallery(getVisibleAreaBounds(map))) {
			if (moveToFirst()) {
				val idColIdx = getColumnIndex("id")

				val ids = ArrayList<Long>()  // collect ids
				do {
					ids.add(getLong(idColIdx))
				}
				while (moveToNext())

				close()  // close cursor
				ids
			}
			else arrayListOf<Long>()
		}

		showGalleryPois(visiblePoiIds)  // show only visible gallery-pois
		return visiblePoiIds.size
	}

	/** Shows visible cycling activities.
	 * @returns number of visible cycling activities (for logging purpose). */
	private fun showVisibleCycleActivity(): Int {
		val map = binding.mapView.getMapboxMap()

		val visibleCycle = with(_db!!.queryCycle(getVisibleAreaBounds(map))) {
			if (moveToFirst()) {
				val logPathColIdx = getColumnIndex("logPath")

				val paths = ArrayList<String>()  // collect paths
				do {
					paths.add(getString(logPathColIdx))
				}
				while (moveToNext())

				close()
				paths
			}
			else arrayListOf<String>()
		}

		// show trips
		_tripLayer.showTrip(visibleCycle)
		return visibleCycle.size
	}

	companion object {
		const val TAG = "FirstFragment"
	}

	private var _db: MainDb? = null  // TODO: any way to avoid nullable type and var
	private lateinit var _photoIcon: Bitmap
	private lateinit var _poiIcon: Bitmap
	private lateinit var _loveIcon: Bitmap
	private lateinit var _starIcon: Bitmap
	private lateinit var _jewelryIcon: Bitmap
	private lateinit var _galleryLayer: GalleryLayer
	private lateinit var _tripLayer: CycleLayer
	private lateinit var _locationLayer: LocationLayer

	private val _permissions = Permissions(this)

	// Location related stuff
	private var _haveLocation = false
}
