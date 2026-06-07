package com.verinite.common.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends BaseException {
    public ServiceUnavailableException(String message) {
        super(message, "SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }
}