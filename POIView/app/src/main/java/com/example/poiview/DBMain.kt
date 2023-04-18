package com.example.poiview

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

// Main database manipulation class. TODO: try to figure out better name! AppDB?
class DBMain {
	data class PoiRecord(val lon: Double, val lat: Double, val name: String)
	data class GalleryRecord(val lon: Double, val lat: Double, val date: Long, val path: String)

	constructor(context: Context) {
		db = DBOpenHelper(context).let {
			it.writableDatabase
		}

		assert(db != null){"database '$dbName' failed to open"}
	}

	fun queryPois(): Cursor {
		// TODO: we have PoiRecord, should we use it there?
		return db!!.rawQuery("SELECT * FROM $poiTable", null)
	}

	fun queryGallery(): Cursor {
		// TODO: we have GalleryRecord, should we use it there?
		return db!!.rawQuery("SELECT * FROM $galleryTable", null)
	}

	/* param photo Photography file path. */
	fun inGallery(photo: String): Boolean {
		val cursor = db!!.rawQuery("SELECT id FROM $galleryTable WHERE path=?", arrayOf<String>(photo))
		return cursor.count != 0
	}

	fun addToGallery(item: GalleryRecord) {
		insertGallery(db!!, item)
		// TODO: no mechanism to let all know insert failed
	}

	private fun insertGallery(db: SQLiteDatabase, item: GalleryRecord) {
		val values = ContentValues()
		with(values) {
			put(galleryLonCol, item.lon)
			put(galleryLatCol, item.lat)
			put(galleryDateCol, item.date)
			put(galleryPathCol, item.path)
		}

		val id = db!!.insert(galleryTable, null, values)
		assert(id != -1L){"inserting '${item.path}' record to $galleryTable table failed"}
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
			Log.d("DBMain", "database upgraded")
			db!!.execSQL("DROP TABLE IF EXISTS $poiTable")
			db!!.execSQL("DROP TABLE IF EXISTS $galleryTable")
			populateDb(db)
		}

		private fun populateDb(db: SQLiteDatabase) {
			db!!.execSQL(CREATE_TABLE_POI_SQL)
			populatePoiWithSamples(db)

			db!!.execSQL(CREATE_TABLE_GALLERY_SQL)
			populateGalleryWithSamples(db)
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
				GalleryRecord(15.0505333, 50.7888681, 1676039073, "/test/photo1.jpg"),
				GalleryRecord(15.0505714, 50.7823372, 1676035473, "/test/photo2.jpg"),
				GalleryRecord(15.0471172, 50.7726478, 1676031873, "/test/photo3.jpg"),
				GalleryRecord(15.0692225, 50.7652283, 1676028273, "/test/photo4.jpg"),
				GalleryRecord(15.0813903, 50.7431717, 1676024673, "/test/photo5.jpg"),
				GalleryRecord(15.0546950, 50.7302550, 1676021073, "/test/photo6.jpg"),
				GalleryRecord(15.0552931, 50.7191353, 1676017473, "/test/photo7.jpg"),
				GalleryRecord(15.0761556, 50.7026367, 1676013873, "/test/photo8.jpg"),
				GalleryRecord(15.1169319, 50.6991692, 1676010273, "/test/photo9.jpg"),
				GalleryRecord(15.1413058, 50.7085036, 1676006673, "/test/photo10.jpg")
			)

			gallerySamples.forEach {
				insertGallery(db, it)
			}
		}

		private var context: Context? = null
	}

	private val dbName = "test"
	private val dbVersion = 5

	// poi table
	private val poiTable = "poi"
	private val poiIdCol = "id"
	private val poiLonCol = "lon"
	private val poiLatCol = "lat"
	private val poiNameCol = "name"

	// gallery table
	private val galleryTable = "gallery"
	private val galleryIdCol = "id"
	private val galleryLonCol = "lon"
	private val galleryLatCol = "lat"
	private val galleryDateCol = "date"
	private val galleryPathCol = "path"

	private val CREATE_TABLE_POI_SQL = "CREATE TABLE $poiTable ($poiIdCol INTEGER PRIMARY KEY, $poiLonCol REAL, $poiLatCol REAL, $poiNameCol TEXT);"
	private val CREATE_TABLE_GALLERY_SQL = "CREATE TABLE $galleryTable ($galleryIdCol INTEGER PRIMARY KEY, $galleryLonCol REAL, $galleryLatCol REAL, $galleryDateCol INTEGER, $galleryPathCol TEXT);"
	private var db: SQLiteDatabase? = null
}
