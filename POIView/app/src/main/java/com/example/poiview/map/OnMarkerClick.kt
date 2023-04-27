package com.example.poiview.map

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.poiview.DBMain
import com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import java.io.File

class OnMarkerClick(val db: DBMain, val parentFragment: Fragment): OnPointAnnotationClickListener {  // TODO: MarkerClicked?
	override fun onAnnotationClick(annotation: PointAnnotation): Boolean {
		// TODO: this needs to handle both POI and photo markers
		// TODO: how to handle possible missing table, id fields without `?.` operator ?
		val markerData = annotation.getData()?.asJsonObject
		val table = markerData?.get("table")?.asString
		val id = markerData?.get("id")?.asLong
		Log.d(TAG, "annotation (table=$table, id=$id) clicked")

		if (table == "gallery") {
			val photoPath = db.getPhotoPath(id!!)
			Log.d(TAG, "photography $photoPath clicked")
			Log.d(TAG, "external-storage-directory=${Environment.getExternalStorageDirectory()}")

			val photoUri = FileProvider.getUriForFile(parentFragment.requireContext(), GALLERY_AUTHORITY, File(photoPath))
			Log.d(TAG, "photoUri=$photoUri")

			val intent = Intent(Intent.ACTION_VIEW).apply {
				data = photoUri
				flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
			}

			parentFragment.startActivity(intent)
		}

		return true
	}

	companion object {
		val TAG = "poiview.map"
		val GALLERY_AUTHORITY = "com.example.poiview.fileprovider"
	}
}