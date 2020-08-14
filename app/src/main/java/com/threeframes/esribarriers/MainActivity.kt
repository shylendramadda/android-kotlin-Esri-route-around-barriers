package com.threeframes.esribarriers

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.esri.arcgisruntime.geometry.*
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.symbology.*
import com.esri.arcgisruntime.tasks.networkanalysis.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {

    // Track the current state of the sample.
    private lateinit var currentSampleState: SampleState
    private val defaultMapScale: Double = 102000.0
    private val spatialReference = SpatialReferences.getWgs84()
    // Graphics overlays to maintain the stops, barriers, and route result.
    private lateinit var routeOverlay: GraphicsOverlay
    private lateinit var stopsOverlay: GraphicsOverlay
    private lateinit var barriersOverlay: GraphicsOverlay
    private lateinit var graphicsOverlay: GraphicsOverlay
    private lateinit var planarGraphicsOverlay: GraphicsOverlay
    private lateinit var tapLocationsOverlay: GraphicsOverlay
    // The route task manages routing work.
    private lateinit var routeTask: RouteTask
    // The route parameters defines how the route will be calculated.
    private var routeParameters: RouteParameters? = null
    // Symbols for displaying the barriers and the route line.
    private lateinit var routeSymbol: Symbol
    private lateinit var barrierSymbol: Symbol
    // Symbol to add stop point
    private lateinit var stopMarker: PictureMarkerSymbol
    // Hold a list of directions.
    private var directions = mutableListOf<DirectionManeuver>()
    private var currentViewpoint = Point(32.7157, -117.1611)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        updateInterfaceState(SampleState.NotReady)

        // Create the map with a basemap.
        val sampleMap = ArcGISMap(Basemap.createTopographic())
        sampleMap.initialViewpoint = Viewpoint(32.7157, -117.1611, 1e5)
        mapView.map = sampleMap
        sampleMap.loadAsync()

        // Create the graphics overlays. These will manage rendering of route, direction, stop, and barrier graphics.
        routeOverlay = GraphicsOverlay()
        stopsOverlay = GraphicsOverlay()
        barriersOverlay = GraphicsOverlay()
        graphicsOverlay = GraphicsOverlay()

        // create a fill symbol for planar buffer polygons
        val planarOutlineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLACK, 2F)
        val planarBufferFillSymbol = SimpleFillSymbol(
            SimpleFillSymbol.Style.SOLID, Color.RED, planarOutlineSymbol
        )

        // create a graphics overlay to display planar polygons and set its renderer
        planarGraphicsOverlay = GraphicsOverlay().apply {
            renderer = SimpleRenderer(planarBufferFillSymbol)
            opacity = 0.2f
        }

        // create a marker symbol for tap locations
        val tapSymbol = SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CROSS, Color.BLACK, 14F)

        // create a graphics overlay to display tap locations for buffers and set its renderer
        tapLocationsOverlay = GraphicsOverlay().apply {
            renderer = SimpleRenderer(tapSymbol)
        }

        // Add graphics overlays to the map view.
        mapView.graphicsOverlays.addAll(
            listOf(
                routeOverlay,
                stopsOverlay,
                barriersOverlay,
                graphicsOverlay,
                planarGraphicsOverlay,
                tapLocationsOverlay
            )
        )

        // Create and initialize the route task.
        routeTask = RouteTask(this@MainActivity, routeServiceUrl)
        routeTask.loadAsync()

        // Get the route parameters from the route task.
        routeTask.addDoneLoadingListener {
            if (routeTask.loadStatus == LoadStatus.LOADED) {
                routeParameters = routeTask.createDefaultParametersAsync().get()
            }
        }

        // Prepare symbols.
        routeSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 2.0f)
        barrierSymbol = SimpleFillSymbol(SimpleFillSymbol.Style.CROSS, Color.RED, null)
        stopMarker = getPictureMarker()

        updateInterfaceState(SampleState.Ready)

        addStopButton.setOnClickListener {
            updateInterfaceState(SampleState.AddingStops)
        }
        addBarrierButton.setOnClickListener {
            updateInterfaceState(SampleState.AddingBarriers)
        }
        resetButton.setOnClickListener {
            updateInterfaceState(SampleState.NotReady)
            stopsOverlay.graphics.clear()
            barriersOverlay.graphics.clear()
            routeOverlay.graphics.clear()
            graphicsOverlay.graphics.clear()
            planarGraphicsOverlay.graphics.clear()
            tapLocationsOverlay.graphics.clear()
            directions.clear()
            updateInterfaceState(SampleState.Ready)
        }
        routeButton.setOnClickListener {
            updateInterfaceState(SampleState.Routing)
            configureThenRoute()
            updateInterfaceState(SampleState.Ready)
        }
        directionsButton.setOnClickListener {
            showDirections()
        }
        nextButton.setOnClickListener {
            startActivity(Intent(this, SecondActivity::class.java))
        }
        addPolygonButton.setOnClickListener {
            graphicsOverlay.graphics.clear()
            addPolygon()
        }
        addPolylineButton.setOnClickListener {
            graphicsOverlay.graphics.clear()
            addPolyline()
        }
        addPointButton.setOnClickListener {
            graphicsOverlay.graphics.clear()
            addPoint()
        }
        addCircleButton.setOnClickListener {
            updateInterfaceState(SampleState.AddingCircle)
        }
        listenToMapTap()
    }

    private fun addCirclePolyGon(mapPoint: Point) {
        // create a planar buffer graphic around the input location at the specified distance
        val bufferGeometryPlanar = GeometryEngine.buffer(mapPoint as Geometry, 10.0)
        val planarBufferGraphic = Graphic(bufferGeometryPlanar)

        // create a graphic for the user tap location
        val locationGraphic = Graphic(mapPoint as Geometry)

        // add the buffer polygons and tap location graphics to the appropriate graphic overlays
        planarGraphicsOverlay.graphics.add(planarBufferGraphic)
        tapLocationsOverlay.graphics.add(locationGraphic)

        // set map view point
        currentViewpoint = Point(mapPoint.x, mapPoint.y)
        mapView.setViewpointCenterAsync(currentViewpoint, 2000.0)
    }

    private fun addPoint() {
        // create point
        val pointGeometry = Point(77.63, 12.95, SpatialReferences.getWgs84())
        // create graphic for point
        val pointGraphic = Graphic(pointGeometry)
        // red diamond point symbol
        val pointSymbol = SimpleMarkerSymbol(
            SimpleMarkerSymbol.Style.CIRCLE,
            ContextCompat.getColor(this, R.color.colorTransparentOrange),
            20f
        )
        // create simple renderer
        val pointRenderer = SimpleRenderer(pointSymbol)
        graphicsOverlay.renderer = pointRenderer
        graphicsOverlay.graphics.add(pointGraphic)
        currentViewpoint = Point(77.63, 12.95, spatialReference)
        mapView.setViewpointCenterAsync(currentViewpoint, defaultMapScale)
    }

    private fun addPolyline() {
        // create line
        val lineGeometry = PolylineBuilder(spatialReference).apply {
            addPoint(77.63, 12.95)
            addPoint(77.646358, 12.978973)
            addPoint(77.691623, 12.974371)
            addPoint(77.63, 12.95)
        }
        // create graphic for polyline
        val lineGraphic = Graphic(lineGeometry.toGeometry())
        // solid blue line symbol
        val lineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 5f)
        // create simple renderer
        val lineRenderer = SimpleRenderer(lineSymbol)
        graphicsOverlay.renderer = lineRenderer
        graphicsOverlay.graphics.add(lineGraphic)
        currentViewpoint = Point(77.63, 12.95, spatialReference)
        mapView.setViewpointCenterAsync(currentViewpoint, defaultMapScale)
    }

    private fun addPolygon() {
        // create polygon
        val polygonGeometry = PolygonBuilder(spatialReference).apply {
            addPoint(77.63, 12.95)
            addPoint(77.636358, 12.978973)
            addPoint(77.691623, 12.974371)
            addPoint(77.63, 12.95)
        }
        // create graphic for polygon
        val polygonGraphic = Graphic(polygonGeometry.toGeometry())
        // solid yellow polygon symbol
        val polygonSymbol = SimpleFillSymbol(SimpleFillSymbol.Style.SOLID, Color.YELLOW, null)
        // create simple renderer
        val polygonRenderer = SimpleRenderer(polygonSymbol)
        graphicsOverlay.renderer = polygonRenderer
        graphicsOverlay.graphics.add(polygonGraphic)
        currentViewpoint = Point(77.63, 12.95, spatialReference)
        mapView.setViewpointCenterAsync(currentViewpoint, defaultMapScale)
    }

    private fun showDirections() {
        if (directions.isEmpty()) {
            showMessage(
                "No directions",
                "Add stops and barriers, then click 'Route' before displaying directions."
            )
            return
        }

        // Create a dialog to show route directions.
        val dialogBuilder = AlertDialog.Builder(this)

        // Create the layout.
        val dialogLayout = LinearLayout(this)

        // Convert the directions list to a suitable format for display.
        val directionTexts = mutableListOf<String>()
        directions.forEach { directionTexts.add(it.directionText) }
        // Create a list box for showing the route directions.
        val directionsList = ListView(this)
        val directionsAdapter =
            ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, directionTexts.toList())
        directionsList.adapter = directionsAdapter
        dialogLayout.addView(directionsList)

        // Add the controls to the dialog.
        dialogBuilder.setView(dialogLayout)
        dialogBuilder.setTitle("Directions")

        // Show the dialog.
        dialogBuilder.create().show()
    }

    private fun configureThenRoute() {
        // Guard against error conditions.
        if (routeParameters == null) {
            showMessage("Route Params", "Route Params not yet loaded. Please try again.")
            return
        }
        if (stopsOverlay.graphics.count() < 2) {
            showMessage("Not enough stops", "Add at least two stops before solving a route.")
            return
        }

        // Clear any existing route from the map.
        routeOverlay.graphics.clear()

        // Configure the route result to include directions and stops.
        routeParameters?.isReturnStops = true
        routeParameters?.isReturnDirections = true

        // Create a list to hold stops that should be on the route.
        val routeStops = mutableListOf<Stop>()

        // Create stops from the graphics.
        for (stopGraphic in stopsOverlay.graphics) {
            // Note: this assumes that only points were added to the stops overlay.
            val stopPoint = stopGraphic.geometry

            // Create the stop from the graphic's geometry.
            val routeStop = Stop(stopPoint as Point)

            // Set the name of the stop to its position in the list.
            routeStop.name = "${stopsOverlay.graphics.indexOf(stopGraphic) + 1}"

            // Add the stop to the list of stops.
            routeStops.add(routeStop)
        }

        // Configure the route parameters with the stops.
        routeParameters?.clearStops()
        routeParameters?.setStops(routeStops)

        // Create a list to hold barriers that should be routed around.
        val routeBarriers = mutableListOf<PolygonBarrier>()

        // Create barriers from the graphics.
        for (barrierGraphic in barriersOverlay.graphics) {
            // Get the polygon from the graphic.
            val barrierPolygon = barrierGraphic.geometry as Polygon

            // Create a barrier from the polygon.
            val routeBarrier = PolygonBarrier(barrierPolygon)

            // Add the barrier to the list of barriers.
            routeBarriers.add(routeBarrier)
        }

        // Configure the route parameters with the barriers.
        routeParameters?.clearPolygonBarriers()
        routeParameters?.setPolygonBarriers(routeBarriers)

        // If the user allows stops to be re-ordered, the service will find the optimal order.
        routeParameters?.isFindBestSequence = reorderStopsCheckbox.isChecked

        // If the user has allowed re-ordering, but has a definite start point, tell the service to preserve the first stop.
        routeParameters?.isPreserveFirstStop = preserveFirstStopCheckbox.isChecked

        // If the user has allowed re-ordering, but has a definite end point, tell the service to preserve the last stop.
        routeParameters?.isPreserveLastStop = preserveLastStopCheckbox.isChecked

        // Calculate and show the route.
        calculateAndShowRoute()
    }

    private fun calculateAndShowRoute() {
        try {
            // Calculate the route.
            val calculatedRoute = routeTask.solveRouteAsync(routeParameters).get()

            // Get the first returned result.
            val firstResult = calculatedRoute.routes.first()

            // Get the route geometry - this is the line that shows the route.
            val calculatedRouteGeometry = firstResult.routeGeometry

            // Create the route graphic from the geometry and the symbol.
            val routeGraphic = Graphic(calculatedRouteGeometry, routeSymbol)

            // Clear any existing routes, then add this one to the map.
            routeOverlay.graphics.clear()
            routeOverlay.graphics.add(routeGraphic)

            // Add the directions to the text box.
            directions = firstResult.directionManeuvers.toMutableList()
        } catch (e: Exception) {
            showMessage(
                "Routing error",
                "Couldn't calculate route. See debug output for details. Message: ${e.message}"
            )
        }
    }

    private fun listenToMapTap() {
        mapView.onTouchListener = object : DefaultMapViewOnTouchListener(this, mapView) {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val screenPoint = android.graphics.Point(
                    e.x.roundToInt(),
                    e.y.roundToInt()
                )
                val mapPoint = mMapView.screenToLocation(screenPoint)
                handleMapTap(mapPoint)
                return true
            }
        }
    }

    private fun showMessage(title: String, detail: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(detail).show()
    }

    @SuppressLint("SetTextI18n")
    private fun updateInterfaceState(newState: SampleState) { // Manage the UI state for the sample.
        currentSampleState = newState
        when (currentSampleState) {
            SampleState.NotReady -> {
                addStopButton.isEnabled = false
                addPolylineButton.isEnabled = false
                resetButton.isEnabled = false
                reorderStopsCheckbox.isChecked = false
                preserveLastStopCheckbox.isChecked = false
                preserveFirstStopCheckbox.isChecked = false
                directionsButton.isEnabled = false
                routeButton.isEnabled = false
                statusLabel.text = "Preparing sample..."
            }
            SampleState.AddingBarriers -> statusLabel.text = "Tap the map to add a barrier."
            SampleState.AddingCircle -> statusLabel.text = "Tap the map to add a circle."
            SampleState.AddingStops -> statusLabel.text = "Tap the map to add a stop."
            SampleState.Ready -> {
                addStopButton.isEnabled = true
                addPolylineButton.isEnabled = true
                resetButton.isEnabled = true
                reorderStopsCheckbox.isEnabled = true
                preserveLastStopCheckbox.isEnabled = true
                preserveFirstStopCheckbox.isEnabled = true
                directionsButton.isEnabled = true
                routeButton.isEnabled = true
                progressIndicator.visibility = View.GONE
                statusLabel.text =
                    "Click 'Add stop' or 'Add barrier', then tap on the map to add stops and barriers."
            }
            SampleState.Routing -> progressIndicator.visibility = View.VISIBLE
        }
    }

    private fun handleMapTap(mapPoint: Point) {
        val geometry = mapPoint as Geometry
        // Normalize geometry - important for geometries that will be sent to a server for processing.
        val geometryNormalized = GeometryEngine.normalizeCentralMeridian(geometry)

        when (currentSampleState) {
            SampleState.AddingBarriers -> {
                // Buffer the tapped point to create a larger barrier.
                val bufferedGeometry = GeometryEngine.bufferGeodetic(
                    geometryNormalized,
                    100.0,
                    LinearUnit(LinearUnitId.METERS),
                    Double.NaN,
                    GeodeticCurveType.GEODESIC
                )

                // Create the graphic to show the barrier.
                val barrierGraphic = Graphic(bufferedGeometry, barrierSymbol)

                // Add the graphic to the overlay - this will cause it to appear on the map.
                barriersOverlay.graphics.add(barrierGraphic)
            }
            SampleState.AddingStops -> {
                // Get the name of this stop.
                val stopName = "${stopsOverlay.graphics.count() + 1}"

                // Create the text symbol for showing the stop.
                val stopSymbol = TextSymbol(
                    14.0f,
                    stopName,
                    Color.WHITE,
                    TextSymbol.HorizontalAlignment.CENTER,
                    TextSymbol.VerticalAlignment.MIDDLE
                )
                stopSymbol.offsetY = 14.0f
                val combinedSymbol = CompositeSymbol(listOf(stopMarker, stopSymbol))
                // Create the graphic to show the stop.
                val stopGraphic = Graphic(geometryNormalized, combinedSymbol)
                // Add the graphic to the overlay - this will cause it to appear on the map.
                stopsOverlay.graphics.add(stopGraphic)
            }
            SampleState.AddingCircle -> {
                addCirclePolyGon(mapPoint)
            }
            else -> {

            }
        }
    }

    private fun getPictureMarker(): PictureMarkerSymbol {
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.icon_stop)
//        val scaledBitmap = Bitmap.createScaledBitmap(icon, 100, 100, false)
        return PictureMarkerSymbol.createAsync(BitmapDrawable(resources, bitmap)).get()
    }

    companion object {
        // URL to the network analysis service.
        private const val routeServiceUrl =
            "https://sampleserver6.arcgisonline.com/arcgis/rest/services/NetworkAnalysis/SanDiego/NAServer/Route"
//            "https://route.arcgis.com/arcgis/rest/services/World/Route/NAServer/Route_World/"
    }

}