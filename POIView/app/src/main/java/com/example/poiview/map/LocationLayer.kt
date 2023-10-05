package com.example.poiview.map

import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import com.mapbox.geojson.Point
import com.mapbox.maps.R
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener

class LocationLayer(private val locationPlugin: LocationComponentPlugin,
	private val locationUpdate: (point: Point) -> Unit) {

	/** Initialize location layer.
	@param fragment Layer owner.
	Note: Expects location permissions are granted. */
	fun initFor(fragment: Fragment) {
		locationPlugin.updateSettings {
			enabled = true
			locationPuck = LocationPuck2D(
				bearingImage = AppCompatResources.getDrawable(
					fragment.requireContext(),
					//R.drawable.mapbox_user_puck_icon,
					R.drawable.mapbox_mylocation_icon_bearing
				),
				shadowImage = AppCompatResources.getDrawable(
					fragment.requireContext(),
					//R.drawable.mapbox_user_icon_shadow,
					R.drawable.mapbox_mylocation_bg_shape
				),
				scaleExpression = interpolate {
					linear()
					zoom()
					stop {
						literal(0.5)
						literal(1.8)
					}
					stop {
						literal(20.0)
						literal(1.0)
					}
				}.toJson(),
				opacity = 0.7f
			)
		}

		locationPlugin.addOnIndicatorPositionChangedListener(onLocationUpdate)
	}

	fun onDestroy() {
		locationPlugin.removeOnIndicatorPositionChangedListener(onLocationUpdate)
	}

	private val onLocationUpdate = OnIndicatorPositionChangedListener {
		locationUpdate(it)
	}
}
