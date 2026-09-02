package com.herafi.user_service.dto;

import com.herafi.user_service.entity.ServiceCity;

public record ServiceCityResponse(
        String code, String displayName) {
    public static ServiceCityResponse from(ServiceCity city) {
        return new ServiceCityResponse(
                city.getCode(),
                city.getDisplayName());
    }
}
