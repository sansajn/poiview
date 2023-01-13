package com.example.poiview

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/* Database unit tests. */
@RunWith(AndroidJUnit4::class)
class DBUnitTest {
	@Test
	fun createDB() {
		val appContext = InstrumentationRegistry.getInstrumentation().targetContext
		val db = DBMain(appContext)
	}

	@Test
	fun readFromDB() {
		val appContext = InstrumentationRegistry.getInstrumentation().targetContext

		val db = DBMain(appContext)
		val poiCursor = db.queryPOIs()

		val idColIdx = poiCursor.getColumnIndex("id")
		assertTrue(idColIdx > -1)

		val lonColIdx = poiCursor.getColumnIndex("lon")
		assertTrue(lonColIdx > -1)

		val latColIdx = poiCursor.getColumnIndex("lat")
		assertTrue(latColIdx > -1)

		val nameColIdx = poiCursor.getColumnIndex("name")
		assertTrue(nameColIdx > -1)

		// check records
		var recordCount = 0
		if (poiCursor.moveToFirst()) {
			do {
				val id = poiCursor.getInt(idColIdx)
				assertTrue("id column is expected to be >= 0 not $id", id >= 0)

				val lon = poiCursor.getDouble(lonColIdx)
				assertTrue("invalid longitude coordinate", lon >= -180.0 && lon <= 180.0)

				val lat = poiCursor.getDouble(latColIdx)
				assertTrue("invalid latitude coordinate", lat >= -90.0 && lat <= 90.0)

				val name = poiCursor.getString(nameColIdx)
				assertFalse("empty name", name.isEmpty())

				recordCount += 1
			}
			while (poiCursor.moveToNext())
		}

		assertEquals(10, recordCount)
	}
}
