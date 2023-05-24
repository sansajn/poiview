package com.example.poiview.gallery

import android.media.ExifInterface
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Gallery photo abstraction.
 *
 * <b>Sample usage:</b>
 * ```
 * val photo = PhotoInfo(photoPath)
 * photo.getLocation()?.let { (lon, lat) ->
 *    // do something with lon, lat ...
 * }
 * ```
 * @param path The photo path (e.g. /storage/12FD-0E09/DCIM/20230429_094037.jpg).
 */
class PhotoInfo(private val path: Path) {
	/** @return optionally photo location as (lon, lat) pair. */
	val location: Pair<Double, Double>?
		get() {
			// lon
			val lonStr = _exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
			val lonRef = _exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
			if (lonStr == null || lonRef == null)
				return null
			val lon: Double = parseExifGPSCoordinate(lonStr, lonRef)

			// lat
			val latStr = _exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
			val latRef = _exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
			if (latStr == null || latRef == null)
				return null
			val lat: Double = parseExifGPSCoordinate(latStr, latRef)

			return lon to lat
	}

	/** @return optionally photo creation date timestamp (posix). */
	val timestamp: Long?
		get() {
			// read creation date from photo
			val dateTimeOriginalAttr = _exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
			val offsetTimeOriginalAttr =
				_exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
			if (dateTimeOriginalAttr == null || offsetTimeOriginalAttr == null)
				return null
			val timestamp = parseExifDateTime(dateTimeOriginalAttr, offsetTimeOriginalAttr)
			return timestamp
		}

	/** @param coord longitude or latitude EXIF GPS coordinate string (e.g. "16/1,29/1,3477119/1000000").
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

	/** @param dateTime One of the DateTime EXIF tag (e.g. DateTimeOriginal).
	param offset Corresponding DateTime offset (e.g. OffsetTimeOriginal) EXIF tag.
	returns Posix UTC timestamp. */
	private fun parseExifDateTime(dateTime: String, offset: String): Long {
		val exifDateTimeFmt = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
		val takenAt = LocalDateTime.parse(dateTime, exifDateTimeFmt)  // local time
		val utcTimestamp: Long = takenAt.toEpochSecond(ZoneOffset.of(offset)) * 1000L
		return utcTimestamp
	}

	private val _exif = ExifInterface(path.toString())
}