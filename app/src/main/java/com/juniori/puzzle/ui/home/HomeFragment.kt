package com.juniori.puzzle.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.juniori.puzzle.R
import com.juniori.puzzle.app.util.extensions.toAddressString
import com.juniori.puzzle.ui.adapter.WeatherRecyclerViewAdapter
import com.juniori.puzzle.databinding.FragmentHomeBinding
import com.juniori.puzzle.domain.TempAPIResponse
import com.juniori.puzzle.ui.adapter.setDisplayName
import com.juniori.puzzle.ui.sensor.SensorActivity
import com.juniori.puzzle.ui.common_ui.StateManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {
    @Inject
    lateinit var stateManager: StateManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val homeViewModel: HomeViewModel by viewModels()
    private lateinit var adapter: WeatherRecyclerViewAdapter
    private var currentPosition = PositionResponse(0.0, 0.0)

    private val applicationContext: Context by lazy { requireActivity().applicationContext }

    private val locationListener = object : LocationListenerCompat {
        override fun onLocationChanged(loc: Location) {
            if (loc.latitude < -90 || loc.latitude > 90 || loc.longitude < -180 || loc.longitude > 180) {
                homeViewModel.setWeatherStateError(WeatherStatusType.WRONG_LOCATION)
                return
            }

            currentPosition = PositionResponse(loc.latitude, loc.longitude)
            setCurrentAddress()
            homeViewModel.getNewWeatherData(currentPosition)
        }

        override fun onProviderDisabled(provider: String) {
            super.onProviderDisabled(provider)
            homeViewModel.setWeatherStateError(WeatherStatusType.LOCATION_SERVICE_OFF)
        }

        override fun onProviderEnabled(provider: String) {
            super.onProviderEnabled(provider)
            refreshWeatherData()
        }
    }


    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isPermitted ->
        homeViewModel.setWeatherStateLoading()

        if (isPermitted.not()) {
            homeViewModel.setWeatherStateError(WeatherStatusType.PERMISSION_DENIED)
        } else {
            if (registerLocationListener()) refreshWeatherData()
            else homeViewModel.setWeatherStateError(WeatherStatusType.LOCATION_SERVICE_OFF)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            vm = homeViewModel
        }
        stateManager.createLoadingDialog(container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = WeatherRecyclerViewAdapter()
        checkPermission()

        binding.run {
            weatherRefreshBtn.setOnClickListener {
                setCurrentAddress()
                refreshWeatherData()
            }

            refreshBtn.setOnClickListener {
                setCurrentAddress()
                refreshWeatherData()
            }

            golfBtnBackground.setOnClickListener {
                val intent = Intent(requireActivity(), SensorActivity::class.java)
                startActivity(intent)
            }

            weatherDetailRecyclerView.adapter = adapter
        }

        lifecycleScope.launchWhenStarted {
            homeViewModel.weatherState.collect { state ->
                if (state != WeatherStatusType.LOADING) stateManager.dismissLoadingDialog()

                when (state) {
                    WeatherStatusType.SUCCESS -> showWeather()
                    WeatherStatusType.PERMISSION_DENIED -> hideWeather(getString(R.string.location_permission))
                    WeatherStatusType.LOCATION_SERVICE_OFF -> hideWeather(getString(R.string.location_service_off))
                    WeatherStatusType.WRONG_LOCATION -> hideWeather(getString(R.string.location_fail))
                    WeatherStatusType.SERVER_ERROR -> hideWeather(getString(R.string.weather_server_error))
                    WeatherStatusType.NETWORK_ERROR -> hideWeather(getString(R.string.network_fail))
                    WeatherStatusType.UNKNOWN_ERROR -> hideWeather(getString(R.string.unknown_error))
                    WeatherStatusType.LOADING -> stateManager.showLoadingDialog()
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            homeViewModel.currentUserInfo.collect { userInfoEntity ->
                if (userInfoEntity is TempAPIResponse.Success) {
                    setDisplayName(binding.userNicknameTv, userInfoEntity.data.nickname)
                }
                else {
                    setDisplayName(binding.userNicknameTv, "어라")
                }
            }
        }

        homeViewModel.run {
            val welcomeTextArray = resources.getStringArray(R.array.welcome_text)
            setWelcomeText(welcomeTextArray)
        }
    }

    override fun onPause() {
        super.onPause()
        stateManager.dismissLoadingDialog()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unregisterLocationListener()
        _binding = null
    }

    @SuppressLint("MissingPermission")
    private fun registerLocationListener(): Boolean {
        val locationManager: LocationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                LOCATION_MIN_TIME_INTERVAL,
                LOCATION_MIN_DISTANCE_INTERVAL,
                locationListener
            )

            true
        }
        else {
            false
        }
    }

    private fun unregisterLocationListener() {
        val locationManager: LocationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.removeUpdates(locationListener)
    }

    private fun setCurrentAddress() {
        val geoCoder = Geocoder(applicationContext)

        try {
            val address = geoCoder.getFromLocation(currentPosition.lat, currentPosition.lon, ADDRESS_MAX_RESULT)
                ?.get(0)
                ?.toAddressString() ?: ""
            homeViewModel.setCurrentAddress(address)
        } catch (e: Exception) {
            homeViewModel.setCurrentAddress(getString(R.string.location_fail))
        }
    }

    private fun refreshWeatherData() {
        val locationManager: LocationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            homeViewModel.setWeatherStateError(WeatherStatusType.PERMISSION_DENIED)
            return
        }
        else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            homeViewModel.setWeatherStateError(WeatherStatusType.LOCATION_SERVICE_OFF)
            return
        }

        setCurrentAddress()
        homeViewModel.getNewWeatherData(currentPosition)
    }

    private fun checkPermission() = locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun showWeather() {
        binding.weatherLayout.isVisible = true
        binding.weatherFailLayout.isVisible = false
    }

    private fun hideWeather(text: String) {
        binding.weatherLayout.isVisible = false
        binding.weatherFailLayout.isVisible = true
        binding.errorReasonText.text = text
    }

    companion object {
        private const val LOCATION_MIN_TIME_INTERVAL = 3000L
        private const val LOCATION_MIN_DISTANCE_INTERVAL = 30f
        private const val ADDRESS_MAX_RESULT = 1
    }
}

