package com.example.poiview.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.poiview.Timestamp
import com.mapbox.maps.CoordinateBounds
import java.time.LocalDate
import kotlin.math.max

// Application main database manipulation class. TODO: try to figure out better name! db.App?
class MainDb {
	data class PoiRecord(val lon: Double, val lat: Double, val name: String)
	data class GalleryRecord(val lon: Double?, val lat: Double?, val date: Long, val path: String)

	/** Cycle table record represents cycle activity.
	 * @param date posix timestamp activity date.
	 * @param logPath path to activity log (GPX) file.
	 * @param minLon, minLat, maxLon, maxLat represents axis aligned bounding box of activity. */
	data class CycleRecord(val title: String, val date: Long, val logPath: String,
		val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double)

	constructor(context: Context) {
		db = DBOpenHelper(context).let {
			it.writableDatabase
		}

		assert(db != null){"database '$dbName' failed to open"}
	}

	fun queryPois(): Cursor {
		// TODO: cursor needs to be closed with close() call, which needs to be done on caller side poor implementation

		// TODO: we have PoiRecord, should we use it there?
		return db!!.rawQuery("SELECT * FROM $poiTable", null)
	}

	// TODO: this can be removed only used in unit tests
	/* Get whole gallery table content as Cursor. */
	fun queryGallery(): Cursor {
		// TODO: cursor needs to be closed with close() call, which needs to be done on caller side poor implementation

		// TODO: we have GalleryRecord, should we use it there?
		return db!!.rawQuery("SELECT * FROM $galleryTable", null)
	}

	/** Note: Returned cursor needs to be closed with close() function call on a caller side, do not forget! */
	fun queryGallery(ids: ArrayList<Long>): Cursor {
		return queryGallery(ids, listOf("*"))
	}

	/** @returns listed table columns for specified gallery items.
	 * Note: Returned cursor needs to be closed with close() function call on a caller side, do not forget! */
	fun queryGallery(ids: ArrayList<Long>, columns: List<String>): Cursor {
		assert(columns.isNotEmpty()) {"we expect not empty list of table columns"}
		return db!!.rawQuery(
			"SELECT ${columns.joinToString(", ")} FROM $galleryTable WHERE id IN (${ids.joinToString(",")})",
			null)
	}

	/** @returns gallery items from inside the specified bounding box as table Cursor which needs to
	 * be explicitly closed by caller.
	 * Note: columns specific queryGallery() implementation should be preferred. */
	fun queryGallery(areaBBox: CoordinateBounds): Cursor {
		return queryGallery(areaBBox, listOf("*"))
	}

	/** @returns listed table columns for gallery items from inside the specified bounding
	 * box as table Cursor.
	 * Note: Returned cursor needs to be closed with close() function call on a caller side, do not forget! */
	fun queryGallery(areaBBox: CoordinateBounds, columns: List<String>): Cursor {
		assert(columns.isNotEmpty()) {"we expect not empty list of table columns"}
		/*SELECT lon, lat, date FROM gallery
			WHERE lon BETWEEN 15.0471172 AND 15.1413058 AND
		lat BETWEEN 50.6991692 AND 50.7888681;*/
		val minPos = areaBBox.southwest
		val maxPos = areaBBox.northeast
		return db!!.rawQuery(
			"SELECT ${columns.joinToString(", ")} FROM $galleryTable WHERE $galleryLonCol BETWEEN ${minPos.longitude()} AND ${maxPos.longitude()} AND $galleryLatCol BETWEEN ${minPos.latitude()} AND ${maxPos.latitude()}",
			null)
	}

	/** @returns gallery items (photos) with location from specified day. */
	fun queryGallery(date: LocalDate): Cursor {
		// TODO: cursor needs to be closed with close() call, which needs to be done on caller side poor implementation
		/*SELECT * FROM gallery
			WHERE date BETWEEN day AND day.at(23, 59, 59)
			AND lon IS NOT NULL AND lat IS NOT NULL */
		val fromTimestamp = Timestamp.fromDate(date)
		val toTimestamp = Timestamp.fromDateTime(date.atTime(23, 59, 59))
		return db!!.rawQuery(
			"SELECT * FROM $galleryTable WHERE $galleryDateCol BETWEEN $fromTimestamp AND $toTimestamp AND $galleryLonCol IS NOT NULL AND $galleryLatCol IS NOT NULL", null)
	}

	/** @param photo Photography file path. */
	fun inGallery(photo: String): Boolean {
		return galleryPhotoId(photo) != -1L
	}

	/** @param photo Photo file path.
	 * @returns photo ID or -1L in case photo is not in table. */
	fun galleryPhotoId(photo: String): Long {
		val cursor = db!!.rawQuery("SELECT id FROM $galleryTable WHERE path=?", arrayOf<String>(photo))
		val id = if (cursor.moveToFirst()) {
			cursor.getLong(cursor.getColumnIndexOrThrow("id"))
		} else -1L

		cursor.close()

		return id
	}

	/** @returns number of gallery records, -1 in case or error. */
	fun galleryCount(): Int {
		val cursor = db!!.rawQuery("SELECT COUNT(*) FROM $galleryTable", null)
		val count = if (cursor.moveToFirst())
			cursor.getInt(0)
		else -1
		cursor.close()
		return count
	}

	/** @returns number of gallery records with valid location (lon, lat columns). */
	fun galleryWithLocationCount(): Int {
		val cursor = db!!.rawQuery("SELECT COUNT($galleryLonCol), COUNT($galleryLatCol) FROM $galleryTable", null)
		val count = if (cursor.moveToFirst())
			max(cursor.getInt(0), cursor.getInt(1))
		else -1
		cursor.close()
		return count
	}

	/** Adds image into gallery table.
	 * @return new record id, in case of error -1L is returned. */
	fun addToGallery(item: GalleryRecord): Long {
		return insertGallery(db!!, item)
	}

	/** @returns cycling activities from inside the specified bounding box as table Cursor which needs to
	 * be explicitly closed by caller. */
	fun queryCycle(areaBBox: CoordinateBounds): Cursor {
//		SELECT * FROM cycle
//		WHERE maxLon < 15.0471172 OR minLon > 15.1413058 OR
//			maxLat < 50.6991692 OR minLat > 50.7888681;
		val bMinPos = areaBBox.southwest
		val bMaxPos = areaBBox.northeast
		return db!!.rawQuery(
			"SELECT * FROM $cycleTable WHERE NOT ($cycleMaxLonCol < ${bMinPos.longitude()} OR $cycleMinLonCol > ${bMaxPos.longitude()} OR $cycleMaxLatCol < ${bMinPos.latitude()} OR $cycleMinLatCol > ${bMaxPos.latitude()})",
			null)
	}

	/** Adds to cycle activity table. */
	fun addToCycle(item: CycleRecord): Long {
		return insertCycle(db!!, item)
	}

	/** @returns number of cycling activities records, -1 in case or error. */
	fun cycleCount(): Int {
		val cursor = db!!.rawQuery("SELECT COUNT(*) FROM $cycleTable", null)
		val count = if (cursor.moveToFirst())
			cursor.getInt(0)
		else -1
		cursor.close()
		return count
	}

	fun inCycleActivities(gpx: String): Boolean {
		return cycleActivityId(gpx) != -1L
	}

	/** @param gpx activity log file path.
	 * @returns activity ID or -1L in case activity not yet in table. */
	fun cycleActivityId(gpx: String): Long {
		val cursor = db!!.rawQuery("SELECT id FROM $cycleTable WHERE ${cycleLogPathCol}=?", arrayOf(gpx))
		val id = if (cursor.moveToFirst()) {
			cursor.getLong(cursor.getColumnIndexOrThrow("id"))
		} else -1L

		cursor.close()

		return id
	}

	/* pram id gallery item id of  photography
	throws an exception in case photography ID not found in gallery table */
	fun getPhotoPath(id: Long): String {  // TODO: do we have path type?
		val cursor = db!!.rawQuery("SELECT path FROM $galleryTable WHERE id=?", arrayOf<String>(id.toString()))
		assert(cursor.count > 0)

		val pathIdx = cursor.getColumnIndex("path")
		assert(pathIdx != -1)

		return if (cursor.moveToFirst()) {
			val path = cursor.getString(pathIdx)
			cursor.close()
			path
		}
		else {
			// TODO: resource leek in case of exception we need to call cursor.close()
			throw Exception("photo id=$id not found in gallery table")
		}
	}

	private fun insertGallery(db: SQLiteDatabase, item: GalleryRecord): Long {
		val values = ContentValues().apply {
			put(galleryLonCol, item.lon)
			put(galleryLatCol, item.lat)
			put(galleryDateCol, item.date)
			put(galleryPathCol, item.path)
		}

		// note: following only works for INTEGER PRIMARY KEY tables where rowid is the same as key.
		val id = db.insert(galleryTable, null, values)
		assert(id != -1L){"inserting '${item.path}' record to $galleryTable table failed"}
		return id
	}

	private fun insertCycle(db: SQLiteDatabase, item: CycleRecord): Long {
		val content = ContentValues().apply {
			put(cycleTitleCol, item.title)
			put(cycleDateCol, item.date)
			put(cycleLogPathCol, item.logPath)
			put(cycleMinLonCol, item.minLon)
			put(cycleMinLatCol, item.minLat)
			put(cycleMaxLonCol, item.maxLon)
			put(cycleMaxLatCol, item.maxLat)
		}

		// note: following only works for INTEGER PRIMARY KEY tables where rowid is the same as key.
		val id = db.insert(cycleTable, null, content)
		assert(id != -1L){"inserting '${item.logPath}' record to $cycleTable table failed"}
		return id
	}

	inner class DBOpenHelper : SQLiteOpenHelper {
		constructor(context: Context) : super(context, dbName, null, dbVersion) {
			Log.d("DBMain", "DBOpenHelper()")
			this.context = context
		}

		override fun onOpen(db: SQLiteDatabase?) {
			super.onOpen(db)

			// TODO: implement all tables are present check
		}

		override fun onCreate(db: SQLiteDatabase?) {
			Log.d("DBMain", "database created")
			populateDb(db!!)

			// TODO: Toast can not be used in unit tests
			// Toast.makeText(context, "database created", Toast.LENGTH_LONG).show()

			// TODO: log into debug channel function called
		}

		override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
			Log.d("DBMain", "database upgraded (old data dropped)")
			db!!.execSQL("DROP TABLE IF EXISTS $poiTable")
			db!!.execSQL("DROP TABLE IF EXISTS $galleryTable")
			db!!.execSQL("DROP TABLE IF EXISTS $cycleTable")
			populateDb(db)
		}

		private fun populateDb(db: SQLiteDatabase) {
			db.execSQL(CREATE_TABLE_POI_SQL)
			populatePoiWithSamples(db)

			db.execSQL(CREATE_TABLE_GALLERY_SQL)
			populateGalleryWithSamples(db)

			db.execSQL(CREATE_TABLE_CYCLE_SQL)
			// there are not any samples there yet ...
		}

		private fun populatePoiWithSamples(db: SQLiteDatabase) {
			val poiSamples = listOf(
				PoiRecord(14.4499106, 50.1044106, "Holesovice"),
				PoiRecord(14.4539178, 50.0964894, "Karlin"),
				PoiRecord(14.4644267, 50.0847706, "Zizkov"),
				PoiRecord(14.4530325, 50.0748011, "Vinohrady"),
				PoiRecord(14.4260389, 50.1175831, "Troja"),
				PoiRecord(14.4452650, 50.1255628, "Kobylisy"),
				PoiRecord(14.4180567, 50.0833386, "Stare Mesto"),
				PoiRecord(14.4254381, 50.0788497, "Nove Mesto"),
				PoiRecord(14.4188719, 50.0636178, "Vysehrad"),
				PoiRecord(14.4028644, 50.0843850, "Mala Strana")
			)

			poiSamples.forEach {
				val values = ContentValues()
				with(values) {
					put(poiLonCol, it.lon)
					put(poiLatCol, it.lat)
					put(poiNameCol, it.name)
				}

				val id = db?.insert(poiTable, null, values)
				assert(id != -1L){"inserting '${it.name}' record to $poiTable table failed"}
			}
		}

		private fun populateGalleryWithSamples(db: SQLiteDatabase) {
			val gallerySamples = listOf(
				// day 1
				GalleryRecord(15.0505333, 50.7888681, 1676039073, "/test/photo1.jpg"),
				GalleryRecord(15.0505714, 50.7823372, 1676035473, "/test/photo2.jpg"),
				GalleryRecord(15.0471172, 50.7726478, 1676031873, "/test/photo3.jpg"),
				GalleryRecord(15.0692225, 50.7652283, 1676028273, "/test/photo4.jpg"),
				GalleryRecord(15.0813903, 50.7431717, 1676024673, "/test/photo5.jpg"),
				GalleryRecord(15.0546950, 50.7302550, 1676021073, "/test/photo6.jpg"),
				GalleryRecord(15.0552931, 50.7191353, 1676017473, "/test/photo7.jpg"),
				GalleryRecord(15.0761556, 50.7026367, 1676013873, "/test/photo8.jpg"),
				GalleryRecord(15.1169319, 50.6991692, 1676010273, "/test/photo9.jpg"),
				GalleryRecord(15.1413058, 50.7085036, 1676006673, "/test/photo10.jpg"),

				// day 2
				GalleryRecord(15.058683875152184, 50.78586129933609, 1697209758, "/test/photo11.jpg"),
				GalleryRecord(15.055410817714716, 50.775168724244, 1697209758, "/test/photo12.jpg"),
				GalleryRecord(15.047500928906288, 50.763956141091455, 1697209758, "/test/photo13.jpg"),
				GalleryRecord(15.060593158657781, 50.75377623867834, 1697209758, "/test/photo14.jpg"),
				GalleryRecord(15.068230292678606, 50.74911690068552, 1697209758, "/test/photo15.jpg"),
				GalleryRecord(15.078867729352226, 50.745147469178676, 1697209758, "/test/photo16.jpg"),
				GalleryRecord(15.078049464992489, 50.73841592483285, 1697209758, "/test/photo17.jpg"),
				GalleryRecord(15.074230897981323, 50.733755058014935, 1697209758, "/test/photo18.jpg"),
				GalleryRecord(15.068503047466123, 50.726676558487156, 1697209758, "/test/photo19.jpg"),
				GalleryRecord(15.070139576184147, 50.72115113274285, 1697209758, "/test/photo20.jpg"),
				GalleryRecord(15.089505166024452, 50.70474368638162, 1697209758, "/test/photo21.jpg")
			)

			gallerySamples.forEach {
				insertGallery(db, it)
			}
		}

		private var context: Context? = null
	}

	private val dbName = "test"
	private val dbVersion = 7

	// poi table
	private val poiTable = "poi"
	private val poiIdCol = "id"
	private val poiLonCol = "lon"
	private val poiLatCol = "lat"
	private val poiNameCol = "name"

	// gallery table
	private val galleryTable = "gallery"  // do not want to share with outside world
	private val galleryPathCol = "path"

	companion object {
		// gallery table
		const val galleryIdCol = "id"
		const val galleryDateCol = "date"
		const val galleryLonCol = "lon"
		const val galleryLatCol = "lat"
	}

	// cycle table
	private val cycleTable = "cycle"
	private val cycleIdCol = "id"
	private val cycleTitleCol = "title"
	private val cycleDateCol = "date"
	private val cycleLogPathCol = "logPath"
	private val cycleMinLonCol = "minLon"
	private val cycleMinLatCol = "minLat"
	private val cycleMaxLonCol = "maxLon"
	private val cycleMaxLatCol = "maxLat"

	private val CREATE_TABLE_POI_SQL = "CREATE TABLE $poiTable ($poiIdCol INTEGER PRIMARY KEY, $poiLonCol REAL, $poiLatCol REAL, $poiNameCol TEXT);"
	private val CREATE_TABLE_GALLERY_SQL = "CREATE TABLE $galleryTable ($galleryIdCol INTEGER PRIMARY KEY, $galleryLonCol REAL, $galleryLatCol REAL, $galleryDateCol INTEGER, $galleryPathCol TEXT);"
	private val CREATE_TABLE_CYCLE_SQL = "CREATE TABLE $cycleTable ($cycleIdCol INTEGER PRIMARY KEY, $cycleTitleCol TEXT, $cycleDateCol INTEGER, $cycleLogPathCol TEXT, $cycleMinLonCol REAL, $cycleMinLatCol REAL, $cycleMaxLonCol REAL, $cycleMaxLatCol REAL);"
	private var db: SQLiteDatabase? = null  // TODO: I want to distinguish between read and write access
}
