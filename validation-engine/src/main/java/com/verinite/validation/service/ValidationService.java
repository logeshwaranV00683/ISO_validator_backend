package com.verinite.validation.service;

import com.verinite.validation.dto.ValidationRequest;
import com.verinite.validation.dto.ValidationResponse;

public interface ValidationService {
    ValidationResponse validate(ValidationRequest request, String userId, String correlationId);
}