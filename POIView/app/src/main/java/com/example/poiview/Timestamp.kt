package com.example.poiview

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** POSIX timestamp (with second precision) helpers. */
class Timestamp {
	companion object {
		/** @returns local date-time represented by POSIX timestamp (in s). */
		fun toDateTime(timestamp: Long): LocalDateTime {
			val dateTime = Instant.ofEpochSecond(timestamp).let {
				LocalDateTime.ofInstant(it, ZoneId.systemDefault())
			}
			return dateTime
		}

		/** @returns local date represented by POSIX timestamp (in s). */
		fun toDate(timestamp: Long): LocalDate {
			val date = with(Instant.ofEpochSecond(timestamp)) {
				atZone(ZoneId.systemDefault()).toLocalDate()
			}
			return date
		}

		/** @returns POSIX timestamp (in s) represented by date-time. */
		fun fromDateTime(date: LocalDateTime): Long {
			return date.toInstant(ZoneOffset.UTC).epochSecond
		}

		/** @returns POSIX timestamp (in s) represented by date. */
		fun fromDate(date: LocalDate): Long {
			return fromDateTime(date.atStartOfDay())
		}
	}
}

