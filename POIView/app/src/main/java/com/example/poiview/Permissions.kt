package com.example.poiview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment

/** Helps to handle permissions for whole app.
Note: Needs to be created before fragment is created during i.e. initialization, onAttach() or onCreate(). */
class Permissions(private val fragment: Fragment) {
	/** Request permissions to access files on SD Card for logfiles from watch. */
	fun requestSdCardPermissionsFor(fragment: Fragment, granted: () -> Unit) {
		if (haveSdCardPermissions()) {
			granted()
		}
		else {  // ask for permissions
			// TODO: rewrite this the same way as in a case of locations
			_sdcardPermissionsGranted = granted
			Log.d(TAG, "requestPermission(ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)")
			with(Intent()) {
				action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
				_sdcardActivityResultLauncher.launch(this)
			}
		}
	}

	/** Request permissions for GPS location. */
	fun requestLocationPermissionsFor(fragment: Fragment, granted: () -> Unit) {
		if (haveLocationPermissions(fragment)) {
			granted()
		}
		else {  // ask for permissions
			_locationPermissionsGranted = granted
			_locationPermissionRequest.launch(arrayOf(
				Manifest.permission.ACCESS_FINE_LOCATION,
				Manifest.permission.ACCESS_COARSE_LOCATION))
		}
	}

	private fun haveLocationPermissions(fragment: Fragment): Boolean = ActivityCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
			|| ActivityCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

	private fun haveSdCardPermissions(): Boolean = Environment.isExternalStorageManager()

	companion object {
		const val TAG = "Permissions"
	}

	private lateinit var _sdcardPermissionsGranted: () -> Unit

	// note: Fragments must call registerForActivityResult() before they are created (i.e. initialization, onAttach(), or onCreate()).
	private val _sdcardActivityResultLauncher =
		fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
			if (haveSdCardPermissions()) {
				Log.d(TAG, "external storage permission granted")
				_sdcardPermissionsGranted()
			} else {
				Log.d(TAG, "external storage permission denied")
			}
		}

	private lateinit var _locationPermissionsGranted: () -> Unit

	private val _locationPermissionRequest =
		fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
			when {
				permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
					// Precise location access granted.
					Log.d(TAG, "ACCESS_FINE_LOCATION granted")
					_locationPermissionsGranted()
				}
				permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
					// Only approximate location access granted.
					Log.d(TAG, "ACCESS_COARSE_LOCATION granted")
					_locationPermissionsGranted()
				}
				permissions.getOrDefault(Manifest.permission.ACCESS_BACKGROUND_LOCATION, false) -> {
					Log.d(TAG, "ACCESS_BACKGROUND_LOCATION granted")
					_locationPermissionsGranted()
				}
				else -> {
					Log.w(TAG, "Location permission not granted (dam user :()")
				}
			}
		}
}
