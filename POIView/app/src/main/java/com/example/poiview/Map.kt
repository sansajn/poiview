package com.example.poiview

import android.util.Log
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.CoordinateBounds
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.ScreenBox
import com.mapbox.maps.ScreenCoordinate
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** @param mapWidth MapView width in pixels.
 * @param mapHeight MapView height in pixels.
 * TODO: mapWidth/Height can probably change in case e.g. keyboard appears so screen bound will not be correct */
class Map(private val map: MapboxMap, private val mapWidth: Int, private val mapHeight: Int) {
	/** @returns visible area geo-rectangle.
	 * Note: I've found that using geo-coordinates (return value) to calculate screen-bounds with
	 * `MapboxMap.pixelForCoordinate()` function doesn't always return correct value in fact most of
	 * the time [-1, -1] received and I didn't figure out why is that. */
	fun visibleAreaBounds(): CoordinateBounds {
		val camOpts = CameraOptions.Builder()
			.zoom(map.cameraState.zoom)
			.center(map.cameraState.center)
			.build()
		return map.coordinateBoundsForCamera(camOpts)
	}

	/** @returns rendered feature on `point` or null (in case there is nothing rendered there) as a Future. */
	fun featureOn(point: Point, layerId: String): CompletableFuture<Feature?> {
		val resultFeature = CompletableFuture<Feature?>()

		val screenPoint = map.pixelForCoordinate(point)
		map.queryRenderedFeatures(
			RenderedQueryGeometry(screenPoint),
			RenderedQueryOptions(listOf(layerId), null)
		) {
			resultFeature.complete(
				if (it.isValue && it.value!!.isNotEmpty()) it.value!![0].feature
				else null
			)
		}

		return resultFeature
	}

	/** @returns List of rendered features on screen as Future. */
	fun featuresOnScreen(layerId: String): CompletableFuture<List<Feature>> {
		val resultFeature = CompletableFuture<List<Feature>>()

		// TODO: mapWidth/Height can probably change in case e.g. keyboard appears so screen bound will not be correct. NOTE: using `MapboxMap.pixelForCoordinate()` function with result of visibleAreaBounds doesn't work it often returns [-1, -1] for unknown reason
		val screenBounds = ScreenBox(ScreenCoordinate(0.0, 0.0),
			ScreenCoordinate(mapWidth.toDouble(), mapHeight.toDouble()))

		map.queryRenderedFeatures(
			RenderedQueryGeometry(screenBounds),
			RenderedQueryOptions(listOf(layerId), null)
		) {
			resultFeature.complete(
				if (it.isValue) it.value!!.map { item -> item.feature }
				else listOf<Feature>()
			)
		}

		return resultFeature
	}

	/** Coroutine attempt to query list of features rendered on screen. */
	suspend fun featuresOnScreenCoro(layerId: String) = suspendCoroutine<List<Feature>> { continuation ->
		// TODO: mapWidth/Height can probably change in case e.g. keyboard appears so screen bound will not be correct. NOTE: using `MapboxMap.pixelForCoordinate()` function with result of visibleAreaBounds doesn't work it often returns [-1, -1] for unknown reason
		val screenBounds = ScreenBox(ScreenCoordinate(0.0, 0.0),
			ScreenCoordinate(mapWidth.toDouble(), mapHeight.toDouble()))

		map.queryRenderedFeatures(
			RenderedQueryGeometry(screenBounds),
			RenderedQueryOptions(listOf(layerId), null)
		) {
			continuation.resume(
				if (it.isValue) it.value!!.map { item -> item.feature }
				else listOf<Feature>()
			)
		}
	}

	companion object {
		const val TAG = "Map"
	}
}

fun mapFromView(view: MapView) = Map(view.getMapboxMap(), view.width, view.height)
