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
import com.example.poiview.map.OnMarkerClick
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

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

		// database initialisation
		_db = DBMain(requireContext())

		mapView = view.findViewById(R.id.mapView)  // TODO: use bindings there
		mapView?.getMapboxMap()?.loadStyleUri(
			Style.MAPBOX_STREETS,
			object : Style.OnStyleLoaded {
				override fun onStyleLoaded(style: Style) {
					Log.d(TAG, "map loaded")
					showPois()
					showGalleryPois()
				}
			})

		// TODO: shouldn't be permission check part of the listGalleryFolder function?
		// check for external storage (all files) permission
		if (checkPermission()) {
			Log.d(TAG, "external storage permission already granted")
			feedDbWithPhotoGalleryContent()
		}
		else {
			Log.d(TAG, "external storage permission not granted, request")
			requestPermission()
			// TODO: are we continue from there after permission granted?
		}
	}

	/* param coord longitude or latitude EXIF GPS coordinate string (e.g. "16/1,29/1,3477119/1000000").
	param ref logitude/latitude reference (hemisphere) */
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
	private fun feedDbWithPhotoGalleryContent() {
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

				addPoiToMap(lon, lat, poiData, R.drawable.marker_empty)
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

				// TODO: restrict pois to 500 otherwise mapview is too much slow
				if (id > 500)
					break

				val poiData = JsonObject().apply {
					add("table", JsonPrimitive("gallery"))
					add("id", JsonPrimitive(id))
				}

				addPoiToMap(lon, lat, poiData, R.drawable.marker_camera)
				Log.d(TAG, "gallery item id=$id ($lat, $lon) added to map")
			}
			while (galleryCursor.moveToNext())
		}

		galleryCursor.close()
	}

	// TODO: moved out gallery stuff

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

		Log.d(TAG, "")

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

	// TODO: rename to something else e.g. insertPoiToMap
	private fun addPoiToMap(lon: Double, lat: Double, poiData: JsonObject, @DrawableRes markerResourceId: Int) {
		// Create an instance of the Annotation API and get the PointAnnotationManager.
		bitmapFromDrawableRes(requireContext(), markerResourceId)?.let {
			val annotationApi = mapView?.annotations

			// TODO: what scope function can I use there to handle manager?
			val pointAnnotationManager = annotationApi?.createPointAnnotationManager(mapView!!)
			pointAnnotationManager?.addClickListener(OnMarkerClick(_db!!, this))  // TODO: OnPointAnnotationClickListener implementation goes there

			// Set options for the resulting symbol layer.
			val pointAnnotationOptions: PointAnnotationOptions =
				PointAnnotationOptions()
					.withPoint(Point.fromLngLat(lon, lat))  // Define a geographic coordinate.
					.withIconImage(it)  // Specify the bitmap you assigned to the point annotation
					.withData(poiData)

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

	// permission stuff TODO: move it into own file
	private fun requestPermission() {
		Log.d(TAG, "requestPermission(ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)")
		with(Intent()) {
			action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
			storageActivityResultLauncher.launch(this)
		}
	}

	// this is called after external storage access granted
	private val storageActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		if (checkPermission()) {
			Log.d(TAG, "external storage permission granted")
			feedDbWithPhotoGalleryContent()
		}
		else {
			Log.d(TAG, "external storage permission denied")
		}
	}

	private fun checkPermission(): Boolean {
		return Environment.isExternalStorageManager()
	}

	companion object {
		val TAG = "mapview"
	}

	private var mapView: MapView? = null  // TODO: can we use lateinit there and avoid null type?
	private var _db: DBMain? = null  // TODO: any way to avoid nullable type and var
}
