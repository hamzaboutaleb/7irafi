package com.herafi.job_service.exceptions;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
	Instant timestamp,
	int status,
	String code,
	String message,
	String path,
	List<FieldError> fieldErrors) {

    public record FieldError(
	    String field,
	    String message) {
    }
}
