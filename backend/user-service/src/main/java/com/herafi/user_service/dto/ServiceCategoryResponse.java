package com.herafi.user_service.dto;

import com.herafi.user_service.entity.ServiceCategory;

public record ServiceCategoryResponse(
        String code,
        String displayName,
        String description) {
    public static ServiceCategoryResponse from(ServiceCategory category) {
        return new ServiceCategoryResponse(
                category.getCode(),
                category.getDisplayName(),
                category.getDescription());
    }
}
