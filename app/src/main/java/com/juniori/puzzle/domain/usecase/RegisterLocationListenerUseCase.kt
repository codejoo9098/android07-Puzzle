package com.juniori.puzzle.domain.usecase

import androidx.core.location.LocationListenerCompat
import com.juniori.puzzle.domain.repository.WeatherRepository
import javax.inject.Inject

class RegisterLocationListenerUseCase @Inject constructor(
    private val repository: WeatherRepository
) {
    operator fun invoke(listener: LocationListenerCompat): Boolean =
        repository.registerLocationListener(listener)
}