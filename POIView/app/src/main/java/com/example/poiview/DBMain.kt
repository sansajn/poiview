package com.example.poiview

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// Main database manipulation class.
class DBMain {
	data class POIRecord(val lon: Double, val lat: Double, val name: String)

	constructor(context: Context) {
		db = DBOpenHelper(context).let {
			it.writableDatabase
		}

		assert(db != null){"database '$dbName' failed to open"}
	}

	fun queryPOIs(): Cursor {
		// TODO: we have POIRecord, should we use it there?
		return db!!.rawQuery("SELECT * FROM $poiTable", null)
	}

	inner class DBOpenHelper : SQLiteOpenHelper {
		constructor(context: Context) : super(context, dbName, null, dbVersion) {
			this.context = context
		}

		override fun onCreate(db: SQLiteDatabase?) {
			db!!.execSQL(CREATE_TABLE_POI_SQL)
			populatePOIWithSamples(db)

			// TODO: Toast can not be used in unit tests
			// Toast.makeText(context, "database created", Toast.LENGTH_LONG).show()
		}

		override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
			// TODO: this will delete all poi table records, handle different way
			db!!.execSQL("DROP TABLE IF EXISTS $poiTable")
		}

		private fun populatePOIWithSamples(db: SQLiteDatabase) {
			val poiSamples = listOf(
				POIRecord(50.1044106, 14.4499106, "Holesovice"),
				POIRecord(50.0964894, 14.4539178, "Karlin"),
				POIRecord(50.0847706, 14.4644267, "Zizkov"),
				POIRecord(50.0748011, 14.4530325, "Vinohrady"),
				POIRecord(50.1175831, 14.4260389, "Troja"),
				POIRecord(50.1255628, 14.4452650, "Kobylisy"),
				POIRecord(50.0833386, 14.4180567, "Stare Mesto"),
				POIRecord(50.0788497, 14.4254381, "Nove Mesto"),
				POIRecord(50.0636178, 14.4188719, "Vysehrad"),
				POIRecord(50.0843850, 14.4028644, "Mala Strana")
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

		var context: Context? = null
	}

	private val dbName = "test"
	private val dbVersion = 1
	private val poiTable = "poi"
	private val poiIdCol = "id"
	private val poiLonCol = "lon"
	private val poiLatCol = "lat"
	private val poiNameCol = "name"

	private val CREATE_TABLE_POI_SQL = "CREATE TABLE $poiTable ($poiIdCol INTEGER PRIMARY KEY, $poiLonCol REAL, $poiLatCol REAL, $poiNameCol TEXT);"
	private var db: SQLiteDatabase? = null
}
