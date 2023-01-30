package com.juniori.puzzle.di

import com.google.gson.Gson
import com.juniori.puzzle.data.datasource.weather.WeatherDataSource
import com.juniori.puzzle.data.datasource.weather.WeatherDataSourceImpl
import com.juniori.puzzle.data.datasource.weather.WeatherService
import com.juniori.puzzle.util.WEATHER_BASE_URL
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WeatherModule {

    @Singleton
    @Provides
    fun providesWeatherDataSource(impl: WeatherDataSourceImpl): WeatherDataSource = impl

    @Singleton
    @Provides
    @Weather
    fun providesWeatherRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .baseUrl(WEATHER_BASE_URL)
            .build()

    @Singleton
    @Provides
    fun providesWeatherService(@Weather retrofit: Retrofit): WeatherService =
        retrofit.create(WeatherService::class.java)

}