package com.example.poiview.gallery

import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.poiview.db.MainDb
import com.example.poiview.map.OnMarkerClick
import java.io.File

/** UI to show gallery photo (from gallery table).
The current implementation opens photo in a default view/app (e.g. Gallery). */
class ShowPhoto(private val db: MainDb, private val parentFragment: Fragment) {
	operator fun invoke(photoId: Long) {
		val photoPath = db.getPhotoPath(photoId)
		Log.d(OnMarkerClick.TAG, "photography $photoPath clicked")
		Log.d(OnMarkerClick.TAG, "external-storage-directory=${Environment.getExternalStorageDirectory()}")

		val photoUri = FileProvider.getUriForFile(parentFragment.requireContext(),
			OnMarkerClick.GALLERY_AUTHORITY, File(photoPath)
		)
		Log.d(OnMarkerClick.TAG, "photoUri=$photoUri")

		with (Intent(Intent.ACTION_VIEW)) {
			data = photoUri
			flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
			parentFragment.startActivity(this)
		}
	}
}
