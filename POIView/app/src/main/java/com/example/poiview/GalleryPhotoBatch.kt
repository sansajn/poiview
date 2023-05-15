package com.example.poiview

import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import java.io.File
import java.nio.file.Paths
import kotlin.system.measureTimeMillis

/** Helper class to get batch of gallery photo IDs (from gallery table). */
class GalleryPhotoBatch(private val parentFragment: Fragment, private val db: DBMain) {  // TODO: this seems to be iterable, can I improve design and make it iterable?
	/** Get next batch of photo IDs (from gallery table) or an empty array otherwise. */
	fun nextBatch(): ArrayList<Long>? {
		// TODO: we can have first 100 photos without GPS data so batch can be 0 length
		val fromIdx = _batchId * _batchSize
		return if (fromIdx < _photos.size) {
			val batchIds = ArrayList<Long>(_batchSize)

			val elapsed = measureTimeMillis {
				val toIdx = if (fromIdx + _batchSize < _photos.size) {
					fromIdx + _batchSize
				}
				else {
					_photos.size
				}

				val photos = _photos.subList(fromIdx, toIdx)

				// TODO: this fills batchIds, can we use filter > map?
				photos.forEach { path ->
					if (path.endsWith(".jpg")) {
						val id = db.galleryPhotoId(path)
						if (id == -1L) {  // create table record
							val photo = PhotoInfo(Paths.get(path))
							photo.location?.let { (lon, lat) ->  // TODO: how to deal with nested let functions
								photo.timestamp?.let { timestamp ->
									val galleryItem = DBMain.GalleryRecord(lon, lat, timestamp, path)
									batchIds.add(db.addToGallery(galleryItem))
								}
							}
						}
						else
							batchIds.add(id)
					}
				}

				Log.d(TAG, "new gallery photos: ${batchIds.size} (${photos.size})")
			}
			Log.d(TAG, "gallery DB update: ${elapsed}ms")

			_batchId += 1
			Log.d(TAG, "batch $_batchId gallery photos requested")

			batchIds
		}
		else {
			null
		}
	}

	/** Lists internal storage gallery folder. */
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

		Log.d(FirstFragment.TAG, "listing internal gallery folder (${galleryFolder.absolutePath}): ${elapsed}ms")

		return result
	}

	/** Lists SD Card gallery folder (e.g. /storage/9C33-6BBD/DCIM) */
	private fun listSdCardGalleryFolder(): ArrayList<String> {
		// figure out DCIM path e.g. /storage/9C33-6BBD/DCIM
		val externalVolumes = MediaStore.getExternalVolumeNames(parentFragment.requireContext())
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

		Log.d(FirstFragment.TAG, "listing SD Card gallery folder ($sdCardDcimPath): ${elapsed}ms")

		return result
	}

	private val _batchSize = 500
	private val _photos = (listGalleryFolder() + listSdCardGalleryFolder()).also {
		Log.d(FirstFragment.TAG, "gallery-folder-photos=${it.size}")
	}
	private var _batchId = 0

	companion object {
		val TAG = "GalleryPhotoBatch"
	}
}