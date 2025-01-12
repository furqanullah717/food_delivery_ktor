package com.codewithfk.services

import com.codewithfk.configs.GoogleConfigs
import com.codewithfk.model.Address
import com.google.maps.GeoApiContext
import com.google.maps.GeocodingApi
import com.google.maps.model.GeocodingResult
import com.google.maps.model.LatLng
import com.google.maps.model.AddressComponentType

object GeocodingService {
    private val geoApiContext = GeoApiContext.Builder()
        .apiKey(GoogleConfigs.mapKey)
        .build()

    fun reverseGeocode(latitude: Double, longitude: Double): Address {
        val results: Array<GeocodingResult> = GeocodingApi.reverseGeocode(geoApiContext, LatLng(latitude, longitude))
            .await()

        if (results.isEmpty()) {
            throw IllegalStateException("No address found for these coordinates")
        }

        val result = results[0]
        
        // Initialize variables to store address components
        var streetNumber = ""
        var route = ""
        var city = ""
        var state = ""
        var country = ""
        var postalCode = ""
        
        // Extract address components
        for (component in result.addressComponents) {
            when {
                component.types.contains(AddressComponentType.STREET_NUMBER) -> streetNumber = component.longName
                component.types.contains(AddressComponentType.ROUTE) -> route = component.longName
                component.types.contains(AddressComponentType.LOCALITY) -> city = component.longName
                component.types.contains(AddressComponentType.ADMINISTRATIVE_AREA_LEVEL_1) -> state = component.shortName
                component.types.contains(AddressComponentType.COUNTRY) -> country = component.longName
                component.types.contains(AddressComponentType.POSTAL_CODE) -> postalCode = component.longName
            }
        }

        // Combine street number and route for address line 1
        val addressLine1 = "$streetNumber $route".trim()

        return Address(
            id = null,
            userId = null,
            addressLine1 = addressLine1,
            addressLine2 = null,
            city = city,
            state = state,
            zipCode = postalCode,
            country = country,
            latitude = latitude,
            longitude = longitude
        )
    }
} 