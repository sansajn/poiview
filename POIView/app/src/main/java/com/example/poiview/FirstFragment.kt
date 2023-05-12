package com.example.poiview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.ExifInterface
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
import com.mapbox.maps.extension.style.expressions.dsl.generated.boolean
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

	/* param coord longitude or latitude EXIF GPS coordinate string (e.g. "16/1,29/1,3477119/1000000").
	param ref longitude/latitude reference (hemisphere) */
	private fun parseExifGPSCoordinate(coord: String, ref: String): Double {
		val coordExpr =
			Regex("""^(?<degNum>\d+)\/(?<degDenom>\d+),(?<minNum>\d+)\/(?<minDenom>\d+),(?<secNum>\d+)\/(?<secDenom>\d+)$""")

		val matchResult = coordExpr.matchEntire(coord)

		matchResult?.let {
			val degNum = it.groups["degNum"]!!.value.toInt()
			val degDenom = it.groups["degDenom"]!!.value.toInt()
			val minNum = it.groups["minNum"]!!.value.toInt()
			val minDenom = it.groups["minDenom"]!!.value.toInt()
			val secNum = it.groups["secNum"]!!.value.toInt()
			val secDenom = it.groups["secDenom"]!!.value.toInt()

			var coordDeg: Double =
				degNum / degDenom + minNum / minDenom / 60.0 + secNum / secDenom / 3600.0
			if (ref == "W" || ref == "S")
				coordDeg *= -1

			return coordDeg
		}

		throw Exception("exception '${coord}' can't be parsed")  // we expect this will not happen
	}

	/* param dateTime One of the DateTime EXIF tag (e.g. DateTimeOriginal).
	param offset Corresponding DateTime offset (e.g. OffsetTimeOriginal) EXIF tag.
	returns Posix UTC timestamp. */
	private fun parseExifDateTime(dateTime: String, offset: String): Long {
		val exifDateTimeFmt = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
		val takenAt = LocalDateTime.parse(dateTime, exifDateTimeFmt)  // local time
		val utcTimestamp: Long = takenAt.toEpochSecond(ZoneOffset.of(offset)) * 1000L
		return utcTimestamp
	}

	// feed gallery DB with photo gallery content
	private fun feedDbWithPhotoGalleryContent() {  // TODO: this function touch private member in a separate thread it should be definitely moved from outside the Fragment
		// TODO: take just first 1000 photos to prevent ANR error
		val photos = (listGalleryFolder() + listSdCardGalleryFolder()).take(1000)
		Log.d(TAG, "gallery-folder-photos=${photos.size}")

		val elapsed = measureTimeMillis {
			photos.forEach {
				val path = it
				if (path.endsWith(".jpg")) {
					if (!_db!!.inGallery(it)) {
						val exif = ExifInterface(path)

						// read GPS position from photo
						val lonStr = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
						val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
						if (lonStr == null || lonRef == null)
							return@forEach
						val lon: Double = parseExifGPSCoordinate(lonStr, lonRef)

						val latStr = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
						val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
						if (latStr == null || latRef == null)
							return@forEach
						val lat: Double = parseExifGPSCoordinate(latStr, latRef)

						// read creation date from photo
						val dateTimeOriginalAttr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
						val offsetTimeOriginalAttr =
							exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
						if (dateTimeOriginalAttr == null || offsetTimeOriginalAttr == null)
							return@forEach
						val timestamp = parseExifDateTime(dateTimeOriginalAttr, offsetTimeOriginalAttr)

						val galleryItem = DBMain.GalleryRecord(lon, lat, timestamp, it)
						_db!!.addToGallery(galleryItem)

						Log.d(TAG, "($lat, $lon), $dateTimeOriginalAttr ($timestamp) -> $it")
					}
//					else
//						Log.d(TAG, "$it item already found in gallery table")
				}
			}
		}

		Log.d(TAG, "gallery DB update: ${elapsed}ms")
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

	private fun showGalleryPois() {
		val galleryCursor = _db!!.queryGallery()

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

				// TODO: restrict POIs to 500 otherwise mapview is too much slow
				if (id > 1000)
					break

//				addPoiToMap(lon, lat, poiData, _photoIcon)  // TODO: remove, replaced by gallery-layer
				_galleryLayer.addPhoto(lon, lat, poiData)

//				Log.d(TAG, "gallery item id=$id ($lat, $lon) added to map")
			}
			while (galleryCursor.moveToNext())
		}

		galleryCursor.close()
	}

	// TODO: move out gallery stuff

	/** Lists internal gallery folder. */
	private fun listGalleryFolder(): ArrayList<String> {
		val galleryFolder =
			Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

		val result = arrayListOf<String>()

		val elapsed = measureTimeMillis {
			// TODO: this seems to be work for map, not forEach
			File(galleryFolder.absolutePath).walk().forEach {
				result.add(it.absolutePath)
			}
		}

		Log.d(TAG, "listing internal gallery folder (${galleryFolder.absolutePath}): ${elapsed}ms")

		return result
	}

	/** Lists SD Card gallery folder (e.g. /storage/9C33-6BBD/DCIM) */
	private fun listSdCardGalleryFolder(): ArrayList<String> {
		// figure out DCIM path e.g. /storage/9C33-6BBD/DCIM
		val externalVolumes = MediaStore.getExternalVolumeNames(requireContext())
		val sdCardVolumes = externalVolumes.filter {
			it != MediaStore.VOLUME_EXTERNAL_PRIMARY && it != MediaStore.VOLUME_EXTERNAL
		}
		if (sdCardVolumes.isEmpty())  // no SD Card
			return arrayListOf<String>()

		// TODO: for now just take the firs one SD Card in system
		val sdCardDcimPath = "/storage/${sdCardVolumes[0].uppercase()}/DCIM"
		Log.d("main", "sdCardDcimPath=$sdCardDcimPath")

		val result = arrayListOf<String>()

		val elapsed = measureTimeMillis {
			// TODO: this seems to be work for map, not forEach
			File(sdCardDcimPath).walk().forEach {
				result.add(it.absolutePath)
			}
		}

		Log.d(TAG, "listing SD Card gallery folder ($sdCardDcimPath): ${elapsed}ms")

		return result
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

		executor.execute {
			feedDbWithPhotoGalleryContent()
			handler.post {
				val elapsedGallery = measureTimeMillis {
					showGalleryPois()
				}
				Log.d(TAG, "showing gallery: ${elapsedGallery}ms")
			}
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
