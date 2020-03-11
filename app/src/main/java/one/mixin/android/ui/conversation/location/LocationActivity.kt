package one.mixin.android.ui.conversation.location

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import javax.inject.Inject
import kotlinx.android.synthetic.main.activity_location.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.api.service.FoursquareService
import one.mixin.android.extension.REQUEST_LOCATION
import one.mixin.android.extension.colorFromAttribute
import one.mixin.android.extension.dp
import one.mixin.android.extension.hideKeyboard
import one.mixin.android.extension.loadImage
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.showKeyboard
import one.mixin.android.ui.common.BaseActivity
import one.mixin.android.vo.Location
import timber.log.Timber

class LocationActivity : BaseActivity(), OnMapReadyCallback {

    @Inject
    lateinit var foursquareService: FoursquareService

    private val ZOOM_LEVEL = 13f
    private var currentPosition: LatLng? = null
    private var selfPosition: LatLng? = null

    private var mapsInitialized = false
    private var onResumeCalled = false
    private var forceUpdate: CameraUpdate? = null

    private val location: Location? by lazy {
        intent.getParcelableExtra<Location>(LOCATION)
    }

    private val locationAdapter by lazy {
        LocationAdapter({
            currentPosition?.let { currentPosition ->
                setResult(Location(currentPosition.latitude, currentPosition.longitude, null, null))
            }
        }, {
            setResult(it)
        })
    }

    private val locationSearchAdapter by lazy {
        LocationSearchAdapter {
            setResult(it)
        }
    }

    private val mLocationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: android.location.Location) {
            currentPosition = LatLng(location.latitude, location.longitude)
            selfPosition = LatLng(location.latitude, location.longitude)
            if (this@LocationActivity.location == null) {
                currentPosition?.let { currentPosition ->
                    moveCamera(currentPosition)
                    isInit = false
                }
                locationAdapter.accurate = getString(R.string.location_accurate, location.accuracy.toInt())
            }
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)
        val locationManager = getSystemService<LocationManager>()
        locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 100f, mLocationListener)
        map_view.onCreate(savedInstanceState)
        ic_back.setOnClickListener {
            if (search_va.displayedChild == 1) {
                search_va.showPrevious()
                search_et.text = null
                search_et.hideKeyboard()
            } else {
                finish()
            }
        }
        ic_search.isVisible = location == null
        pb.isVisible = location == null
        location_go.isVisible = location != null
        location_bottom.isVisible = location == null
        location_recycler.isVisible = location == null
        ic_search.setOnClickListener {
            search_va.showNext()
            search_et.requestFocus()
            search_et.showKeyboard()
        }
        my_location.setOnClickListener {
            selfPosition?.let { selfPosition ->
                moveCamera(selfPosition)
            }
        }
        ic_close.setOnClickListener {
            search_va.showPrevious()
            search_et.text = null
            search_et.hideKeyboard()
        }
        search_et.addTextChangedListener(textWatcher)
        search_et.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    search_et.hideKeyboard()
                    return true
                }
                return false
            }
        })
        MapsInitializer.initialize(MixinApplication.appContext)
        map_view.getMapAsync(this)

        location.notNullWithElse({ location ->
            location_title.text = location.name ?: getString(R.string.location_unnamed)
            location_sub_title.text = location.address
            location.iconUrl.notNullWithElse({
                location_icon.imageTintList = ColorStateList.valueOf(baseContext.colorFromAttribute(R.attr.icon_default))
                location_icon.loadImage(it)
            }, {
                location_icon.setBackgroundResource(R.drawable.ic_current_location)
                location_icon.setImageDrawable(null)
            })
            location_go_iv.setOnClickListener {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}")
                    )
                )
            }
        }, {
            location_recycler.adapter = locationAdapter
            search_recycler.adapter = locationSearchAdapter
        })
    }

    override fun onResume() {
        super.onResume()
        onResumeCalled = true
        if (mapsInitialized) {
            map_view.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        onResumeCalled = false
        if (mapsInitialized) {
            map_view.onPause()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (mapsInitialized) {
            map_view.onLowMemory()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mapsInitialized) {
            map_view.onDestroy()
        }
    }

    private var googleMap: GoogleMap? = null

    private fun moveCamera(latlng: LatLng) {
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latlng, ZOOM_LEVEL))
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        googleMap ?: return
        this.googleMap = googleMap
        if (isNightMode()) {
            val style = MapStyleOptions.loadRawResourceStyle(applicationContext, R.raw.mapstyle_night)
            googleMap.setMapStyle(style)
        }
        mapInit(googleMap)

        with(googleMap) {
            if (location != null) {
                addMarker(
                    MarkerOptions().position(
                        LatLng(location!!.latitude, location!!.longitude)
                    ).icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_map_marker))
                )
                moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location!!.latitude, location!!.longitude), ZOOM_LEVEL))
            }
        }
        mapsInitialized = true
        if (onResumeCalled) {
            map_view.onResume()
        }
    }

    private fun setResult(location: Location) {
        setResult(Activity.RESULT_OK, Intent().putExtra(LOCATION_NAME, location))
        finish()
    }

    private var isInit = true
    fun mapInit(googleMap: GoogleMap) {
        try {
            googleMap.isMyLocationEnabled = true
        } catch (e: Exception) {
            Timber.e(e)
        }
        googleMap.uiSettings?.isMyLocationButtonEnabled = false
        googleMap.uiSettings?.isZoomControlsEnabled = false
        googleMap.uiSettings?.isCompassEnabled = false
        googleMap.uiSettings?.isIndoorLevelPickerEnabled = false
        googleMap.uiSettings?.isRotateGesturesEnabled = false
        googleMap.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                val cameraPosition = googleMap.cameraPosition
                forceUpdate = CameraUpdateFactory.newLatLngZoom(cameraPosition.target, cameraPosition.zoom)
                if (!marker.isVisible && !isInit) {
                    marker.isVisible = true
                    locationSearchAdapter.setMark()
                }
            }
            markerAnimatorSet?.cancel()
            markerAnimatorSet = AnimatorSet()
            markerAnimatorSet?.playTogether(ObjectAnimator.ofFloat(marker, View.TRANSLATION_Y, -8.dp.toFloat()))
            markerAnimatorSet?.duration = 200
            markerAnimatorSet?.start()
        }
        googleMap.setOnCameraMoveListener {}
        googleMap.setOnMarkerClickListener { marker ->
            locationSearchAdapter.setMark(marker.zIndex)
            false
        }

        googleMap.setOnCameraIdleListener {
            markerAnimatorSet?.cancel()
            markerAnimatorSet = AnimatorSet()
            markerAnimatorSet?.playTogether(ObjectAnimator.ofFloat(marker, View.TRANSLATION_Y, 0f))
            markerAnimatorSet?.duration = 200
            markerAnimatorSet?.start()
            googleMap.cameraPosition.target.let { lastLang ->
                if (location == null) {
                    currentPosition = lastLang
                    search(lastLang)
                }
            }
        }

        googleMap.setOnCameraMoveCanceledListener {}
    }

    private var lastSearchJob: Job? = null
    fun search(latlng: LatLng) {
        pb.isVisible = locationAdapter.venues == null
        if (lastSearchJob != null && lastSearchJob?.isActive == true) {
            lastSearchJob?.cancel()
        }
        lastSearchJob = lifecycleScope.launch {
            val result = try {
                foursquareService.searchVenues("${latlng.latitude},${latlng.longitude}")
            } catch (e: Exception) {
                Timber.e(e)
                return@launch
            }
            result.response?.venues.let { list ->
                list?.filter { item ->
                    item.location.address != null
                }.let { data ->
                    locationAdapter.venues = data
                    pb.isVisible = data == null
                }
            }
        }
    }

    private var lastSearchQueryJob: Job? = null
    fun search(query: String) {
        pb.isVisible = locationSearchAdapter.venues == null
        val currentPosition = this.currentPosition ?: return
        if (lastSearchQueryJob != null && lastSearchQueryJob?.isActive == true) {
            lastSearchQueryJob?.cancel()
        }
        lastSearchQueryJob = lifecycleScope.launch {
            val result = try {
                foursquareService.searchVenues("${currentPosition.latitude},${currentPosition.longitude}", query)
            } catch (e: Exception) {
                Timber.e(e)
                return@launch
            }
            result.response?.venues?.filter { item ->
                item.location.address != null
            }.let { data ->
                locationSearchAdapter.venues = data
                pb.isVisible = data == null
                googleMap?.clear()
                data?.forEachIndexed { index, item ->
                    googleMap?.addMarker(
                        MarkerOptions().zIndex(index.toFloat()).position(
                            LatLng(
                                item.location.lat,
                                item.location.lng
                            )
                        ).icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_location_search_maker))
                    )
                }
            }
        }
    }

    private var markerAnimatorSet: AnimatorSet? = null

    private val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            s.notNullWithElse({ s ->
                search_recycler.isVisible = true
                location_recycler.isVisible = false
                val content = s.toString()
                locationSearchAdapter.keyword = content
                search(content)
            }, {
                search_recycler.isVisible = false
                location_recycler.isVisible = true
                locationSearchAdapter.keyword = null
                locationSearchAdapter.venues = null
                locationSearchAdapter.setMark()
                googleMap?.clear()
            })
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    companion object {

        private val LOCATION_NAME = "location_name"
        private val LOCATION = "location"

        fun getResult(intent: Intent): Location? {
            return intent.getParcelableExtra(LOCATION_NAME)
        }

        fun show(fragment: Fragment) {
            Intent(fragment.requireContext(), LocationActivity::class.java).run {
                fragment.startActivityForResult(this, REQUEST_LOCATION)
            }
        }

        fun show(context: Context, location: Location) {
            Intent(context, LocationActivity::class.java).run {
                putExtra(LOCATION, location)
                context.startActivity(this)
            }
        }
    }
}
