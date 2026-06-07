package com.verinite.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends BaseException {
    public BusinessRuleException(String message) {
        super(message, "BUSINESS_RULE_VIOLATION", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}