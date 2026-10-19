package com.herafi.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TimeBucketResponse {
    private String period;
    private long count;
}
