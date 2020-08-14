package com.threeframes.esribarriers

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.esri.arcgisruntime.geometry.PolygonBuilder
import com.esri.arcgisruntime.geometry.Polyline
import com.esri.arcgisruntime.geometry.PolylineBuilder
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.symbology.*
import com.esri.arcgisruntime.tasks.networkanalysis.*
import kotlinx.android.synthetic.main.activity_main.mapView
import kotlinx.android.synthetic.main.activity_main.resetButton
import kotlinx.android.synthetic.main.activity_second.*
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.math.roundToInt


class SecondActivity : AppCompatActivity() {

    private lateinit var mMapView: MapView
    private lateinit var mServiceAreaParameters: ServiceAreaParameters
    private lateinit var mBarrierBuilder: PolylineBuilder

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        // get a reference to the map view
        mMapView = mapView
        // create a map with a streets base map
        val map = ArcGISMap(Basemap.Type.STREETS, 32.73, -117.14, 13)
        mMapView.map = map
        // create service area task from url
        val serviceAreaTask = ServiceAreaTask(this, getString(R.string.san_diego_service_area))
        serviceAreaTask.loadAsync()
        // create default parameters from task
        val serviceAreaParametersFuture =
            serviceAreaTask
                .createDefaultParametersAsync()
        serviceAreaParametersFuture.addDoneListener {
            try {
                mServiceAreaParameters = serviceAreaParametersFuture.get()
                mServiceAreaParameters.polygonDetail = ServiceAreaPolygonDetail.HIGH
                mServiceAreaParameters.isReturnPolygons = true
                // adding another service area of 2 minutes
                // default parameters have a default service area of 5 minutes
                mServiceAreaParameters.defaultImpedanceCutoffs
                    .addAll(listOf(2.0))
            } catch (e: ExecutionException) {
                val error = "Error creating service area parameters: $e"
                Log.e(TAG, error)
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            } catch (e: InterruptedException) {
                val error = "Error creating service area parameters: $e"
                Log.e(TAG, error)
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
        // create graphics overlays to show facilities, barriers and service areas
        val serviceAreasOverlay = GraphicsOverlay()
        val facilityOverlay = GraphicsOverlay()
        val barrierOverlay = GraphicsOverlay()
        mMapView.graphicsOverlays.addAll(
            listOf(
                serviceAreasOverlay,
                barrierOverlay,
                facilityOverlay
            )
        )
        mBarrierBuilder = PolylineBuilder(mMapView.spatialReference)
        val serviceAreaFacilities: MutableList<ServiceAreaFacility> =
            ArrayList()
        val barrierLine = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLACK, 3.0f)
        val fillSymbols = ArrayList<SimpleFillSymbol>()
        fillSymbols.add(
            SimpleFillSymbol(
                SimpleFillSymbol.Style.SOLID,
                ContextCompat.getColor(this, R.color.colorTransparentRed),
                null
            )
        )
        fillSymbols.add(
            SimpleFillSymbol(
                SimpleFillSymbol.Style.SOLID,
                ContextCompat.getColor(this, R.color.colorTransparentOrange),
                null
            )
        )
        // icon used to display facilities to map view
        val facilitySymbol = PictureMarkerSymbol(getString(R.string.hospital_icon_url))
        facilitySymbol.height = 30f
        facilitySymbol.width = 30f

        addFacilityButton.setOnClickListener {
            addFacilityButton.isSelected = true
            addPolylineButton.isSelected = false
        }
        addPolylineButton.setOnClickListener {
            addPolylineButton.isSelected = true
            addFacilityButton.isSelected = false
            mBarrierBuilder = PolylineBuilder(mMapView.spatialReference)
        }
        addPolygonButton.setOnClickListener {
            mMapView.graphicsOverlays.add(
                renderedPolygonGraphicsOverlay()
            )
        }
        showServiceAreasButton.setOnClickListener {
            showServiceAreas(
                serviceAreaFacilities,
                barrierOverlay,
                serviceAreasOverlay,
                serviceAreaTask,
                fillSymbols,
                addFacilityButton,
                addPolylineButton
            )
        }
        resetButton.setOnClickListener {
            clearRouteAndGraphics(
                addFacilityButton,
                addPolylineButton,
                serviceAreaFacilities,
                facilityOverlay,
                serviceAreasOverlay,
                barrierOverlay
            )
        }
        // creates facilities and barriers at user's clicked location
        mMapView.onTouchListener = object : DefaultMapViewOnTouchListener(this, mMapView) {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { // create a point where the user tapped
                val point = Point(
                    e.x.roundToInt(),
                    e.y.roundToInt()
                )
                val mapPoint =
                    mMapView.screenToLocation(point)
                if (addFacilityButton.isSelected) { // create facility from point and display to map view
                    addServicePoint(
                        mapPoint,
                        facilitySymbol,
                        serviceAreaFacilities,
                        facilityOverlay
                    )
                } else if (addPolylineButton.isSelected) { // create barrier and display to map view
                    mBarrierBuilder.addPoint(
                        com.esri.arcgisruntime.geometry.Point(
                            mapPoint.x,
                            mapPoint.y,
                            mMapView.spatialReference
                        )
                    )
                    barrierOverlay.graphics
                        .add(
                            barrierOverlay.graphics.size,
                            Graphic(mBarrierBuilder.toGeometry(), barrierLine as Symbol)
                        )
                }
                return super.onSingleTapConfirmed(e)
            }
        }
    }

    private fun renderedPolygonGraphicsOverlay(): GraphicsOverlay {
        // create polygon
        val polygonGeometry = PolygonBuilder(SpatialReferences.getWgs84()).apply {
            addPoint(77.651264, 13.030071)
            addPoint(77.651480, 13.030082)
            addPoint(77.651091, 13.029735)
            addPoint(77.651447, 13.029651)
        }
        // create graphic for polygon
        val polygonGraphic = Graphic(polygonGeometry.toGeometry())
        // solid yellow polygon symbol
        val polygonSymbol =
            SimpleFillSymbol(SimpleFillSymbol.Style.SOLID, Color.YELLOW, null)
        // create simple renderer
        val polygonRenderer = SimpleRenderer(polygonSymbol)

        // create graphic overlay for polygon and add it to the map view
        return GraphicsOverlay().apply {
            // add graphic to overlay
            graphics.add(polygonGraphic)
            // set the renderer on the graphics overlay to the new renderer
            renderer = polygonRenderer
        }
    }

    /**
     * Add the given point to the list of service areas and use it to create a facility graphic, which is then added to
     * the facility overlay.
     */
    private fun addServicePoint(
        mapPoint: com.esri.arcgisruntime.geometry.Point,
        facilitySymbol: PictureMarkerSymbol,
        serviceAreaFacilities: MutableList<ServiceAreaFacility>,
        facilityOverlay: GraphicsOverlay
    ) {
        val servicePoint =
            com.esri.arcgisruntime.geometry.Point(
                mapPoint.x,
                mapPoint.y,
                mMapView.spatialReference
            )
        serviceAreaFacilities.add(ServiceAreaFacility(servicePoint))
        facilityOverlay.graphics.add(Graphic(servicePoint, facilitySymbol))
    }

    /**
     * Clears all graphics from map view and clears all facilities and barriers from service area parameters.
     */
    private fun clearRouteAndGraphics(
        addFacilityButton: Button,
        addBarrierButton: Button,
        serviceAreaFacilities: MutableList<ServiceAreaFacility>,
        facilityOverlay: GraphicsOverlay,
        serviceAreasOverlay: GraphicsOverlay,
        barrierOverlay: GraphicsOverlay
    ) {
        addFacilityButton.isSelected = false
        addBarrierButton.isSelected = false
        mServiceAreaParameters.clearFacilities()
        mServiceAreaParameters.clearPolylineBarriers()
        serviceAreaFacilities.clear()
        facilityOverlay.graphics.clear()
        serviceAreasOverlay.graphics.clear()
        barrierOverlay.graphics.clear()
        mMapView.graphicsOverlays.clear()
    }

    /**
     * Solves the service area task using the facilities and barriers that were added to the map view.
     * All service areas that are return will be displayed to the map view.
     */
    private fun showServiceAreas(
        serviceAreaFacilities: List<ServiceAreaFacility>,
        barrierOverlay: GraphicsOverlay,
        serviceAreasOverlay: GraphicsOverlay,
        serviceAreaTask: ServiceAreaTask,
        fillSymbols: ArrayList<SimpleFillSymbol>,
        addFacilityButton: Button,
        addBarrierButton: Button
    ) { // need at least one facility for the task to work
        if (serviceAreaFacilities.isNotEmpty()) { // un-select add facility and add barrier buttons
            addFacilityButton.isSelected = false
            addBarrierButton.isSelected = false
            val polylineBarriers: MutableList<PolylineBarrier> =
                ArrayList()
            for (barrierGraphic in barrierOverlay.graphics) {
                polylineBarriers.add(PolylineBarrier(barrierGraphic.geometry as Polyline))
                mServiceAreaParameters.setPolylineBarriers(polylineBarriers)
            }
            serviceAreasOverlay.graphics.clear()
            mServiceAreaParameters.setFacilities(serviceAreaFacilities)
            // find service areas around facility using parameters that were set
            val result =
                serviceAreaTask.solveServiceAreaAsync(mServiceAreaParameters)
            result.addDoneListener {
                try { // display all service areas that were found to mapview
                    val graphics: MutableList<Graphic> =
                        serviceAreasOverlay.graphics
                    val serviceAreaResult = result.get()
                    for (i in serviceAreaFacilities.indices) {
                        val polygons =
                            serviceAreaResult.getResultPolygons(i)
                        // could be more than one service area
                        for (j in polygons.indices) {
                            graphics.add(
                                Graphic(
                                    polygons[j].geometry,
                                    fillSymbols[j % 2]
                                )
                            )
                        }
                    }
                } catch (e: ExecutionException) {
                    val error =
                        if (e.message!!.contains("Unable to complete operation")) "Facility not within San Diego area!$e" else "Error getting the service area result: $e"
                    Log.e(TAG, error)
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                } catch (e: InterruptedException) {
                    val error =
                        if (e.message!!.contains("Unable to complete operation")) "Facility not within San Diego area!$e" else "Error getting the service area result: $e"
                    Log.e(TAG, error)
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(this, "Must have at least one Facility on the map!", Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onPause() {
        mMapView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mMapView.resume()
    }

    override fun onDestroy() {
        mMapView.dispose()
        super.onDestroy()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
