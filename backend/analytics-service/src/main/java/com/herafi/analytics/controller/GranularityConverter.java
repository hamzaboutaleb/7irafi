package com.herafi.analytics.controller;

import com.herafi.analytics.dto.Granularity;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class GranularityConverter implements Converter<String, Granularity> {
    @Override
    public Granularity convert(String value) {
        return switch (value) {
            case "day" -> Granularity.DAY;
            case "week" -> Granularity.WEEK;
            case "month" -> Granularity.MONTH;
            default -> throw new IllegalArgumentException("granularity must be day, week, or month");
        };
    }
}
