package com.herafi.job_service.dto;

import java.util.UUID;

/** Only the public job label is needed when displaying review history. */
public record JobTitleResponse(UUID id, String title) {
}
