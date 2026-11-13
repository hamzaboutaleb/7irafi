package com.herafi.analytics.dto;

import java.util.Locale;

public enum Granularity {
    DAY, WEEK, MONTH;

    public String sqlValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
