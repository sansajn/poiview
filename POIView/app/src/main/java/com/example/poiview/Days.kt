package com.example.poiview

import android.database.Cursor
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.example.poiview.db.MainDb
import com.example.poiview.map.GalleryLayer
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

// TODO: this seems to be source implementation for DayLayer, maybe we should create DaySource instead
/** Helper to work with day functionality. */
class Days(private val map: Map, private val db: MainDb) {
	// TODO: can we make it private somehow?
	data class LocationDate(val location: Pair<Double, Double>, val timestamp: Long)

	// TODO: we need coroutine visibleDaysOnScreen implementation
	// experimental coroutine visibleDaysOnScreen implementation
	suspend fun visibleDaysOnScreenCoro(): List<LineString> {
		// ask map for rendered poi features
		val features = map.featuresOnScreenCoro(GalleryLayer.LAYER_ID)
		Log.d(TAG, "day: found ${features.size} features on screen")
//		features.forEach {
//			Log.d(TAG, "$it")
//		}

		// feature looks this way
//		Feature{type=Feature, bbox=null, id=null,
//			geometry=Point{type=Point, bbox=null, coordinates=[15.050497055053711, 50.78884580700529]},
//			properties={"data":{"id":1,"table":"gallery"}}}

		val ids = ArrayList(features.map {
			val dataJson = it.getProperty("data")!!.asJsonObject
			val photoId = dataJson.get("id").asLong!!
			photoId
		})

		val visiblePhotos = gatherPhotoLocationDate(queryLocationDates(ids))

		// partition them based on day
		val dayPhotos = visiblePhotos.groupBy {
			Timestamp.toDate(it.timestamp)
		}

		// create a LineString for days with more than one photo
		val dayBatch = dayPhotos.filter {
			it.value.size > 1
		}.map { group ->
			// TODO: group photos are unsorted (we need to sort them by timestamp) so they can appear in a wrong order on screen
			val coordinates = group.value.map {
				Point.fromLngLat(it.location.first, it.location.second)
			}
			LineString.fromLngLats(coordinates)
		}

		return dayBatch
	}

	private fun queryLocationDates(ids: ArrayList<Long>): Cursor {
		return db.queryGallery(ids,
			listOf(MainDb.galleryLonCol, MainDb.galleryLatCol, MainDb.galleryDateCol))
	}

	/** @returns visible days on screen as list of LineString patches. */
	fun visibleDaysOnScreen(): List<LineString> {
		/* TODO: instead DB query we need query-rendered-features first and then query IDs into database,
		    but based on number of IDs we want to split DB query into multiple queries (but this should be
		    implemented on MainDB side). */
		// get list of (location, date) items on screen
		val visiblePhotos = gatherPhotoLocationDate(queryLocationDates())

		// TODO: debug code
		// we need to get list of features on screen and use it instead os visiblePhotos
		map.featuresOnScreen(GalleryLayer.LAYER_ID)
			.thenAccept {
				Log.d(TAG, "we found ${it.size} items rendered on screen")
			}
		// end of debug code

		// partition them based on day
		val dayPhotos = visiblePhotos.groupBy {
			Timestamp.toDate(it.timestamp)
		}

		// create a LineString for days with more than one photo
		val dayBatch = dayPhotos.filter {
			it.value.size > 1
		}.map { group ->
			// TODO: group photos are unsorted (we need to sort them by timestamp) so they can appear in a wrong order on screen
			val coordinates = group.value.map {
				Point.fromLngLat(it.location.first, it.location.second)
			}
			LineString.fromLngLats(coordinates)
		}

		return dayBatch
	}

	/** @returns visible days as list of LineString patches including points not on screen.
	 * This implementation was previously used for day visualisation, but the result doesn't
	 * looks good at the end. */
	@Deprecated("Use visibleDaysOnScreen() instead.")
	fun visibleDays(): List<LineString> {
		/* TODO: this is kind of source implementation for DayLayer, maybe we should implement
		    it as DaySource and bound DayLayer instance with source. We can then have layer.show() to
		    trigger layer update or maybe layer.update() */

		// query photos positions for each day
		val dayBatch = visibleDayDates().mapNotNull { day ->
			val dayCoordinates = with(db.queryGallery(day)) {
				if (moveToFirst()) {
					val lonColIdx = getColumnIndex(MainDb.galleryLonCol)
					val latColIdx = getColumnIndex(MainDb.galleryLatCol)
					val coordinates = ArrayList<Point>()
					do {
						coordinates.add(Point.fromLngLat(getDouble(lonColIdx), getDouble(latColIdx)))
					}
					while (moveToNext())

					close()

					// we require at least two coordinates
					if (coordinates.size > 1) coordinates
					else null
				}
				else {
					close()
					null
				}
			}

			dayCoordinates?.let {
				LineString.fromLngLats(it)
			}
		}
		return dayBatch
	}

	/** Collects location & date from DB cursor for each photo. */
	private fun gatherPhotoLocationDate(query: Cursor): ArrayList<LocationDate> {
		return with(query) {
			if (moveToFirst()) {
				val lonColIdx = getColumnIndexOrThrow(MainDb.galleryLonCol)
				val latColIdx = getColumnIndexOrThrow(MainDb.galleryLatCol)
				val dateColIdx = getColumnIndexOrThrow(MainDb.galleryDateCol)

				val photos = ArrayList<LocationDate>()  // collect dates
				do {
					val location = getDouble(lonColIdx) to getDouble(latColIdx)
					val timestamp = getLong(dateColIdx)
					photos.add(LocationDate(location, timestamp))
				}
				while (moveToNext())

				close()  // close cursor
				photos
			}
			else {
				close()
				arrayListOf<LocationDate>()
			}
		}
	}

	/** @returns list of days of photos on screen. */
	private fun visibleDayDates(): List<LocalDate> {
		// TODO: we are working only with dates and do not need all table columns
		val visibleDates = with(db.queryGallery(map.visibleAreaBounds())) {
			if (moveToFirst()) {
				val dateColIdx = getColumnIndex(MainDb.galleryDateCol)

				val dates = ArrayList<Long>()  // collect dates
				do {
					dates.add(getLong(dateColIdx))
				}
				while (moveToNext())

				close()  // close cursor
				dates
			}
			else {
				close()
				arrayListOf<Long>()
			}
		}

		// figure first days on screen
		val visibleDays = visibleDates.map {
			Timestamp.toDate(it)
		}.distinct()

		return visibleDays
	}

	private fun queryLocationDates(): Cursor {
		return db.queryGallery(map.visibleAreaBounds(),
			listOf(MainDb.galleryLonCol, MainDb.galleryLatCol, MainDb.galleryDateCol))
	}

	companion object {
		const val TAG = "Days"
	}
}
