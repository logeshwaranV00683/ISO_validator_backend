package com.verinite.validation.service;

import com.verinite.validation.dto.BuildMessageRequest;
import com.verinite.validation.dto.BuildMessageResponse;
import com.verinite.validation.dto.ValidationRequest;
import com.verinite.validation.dto.ValidationResponse;

public interface ValidationService {

    ValidationResponse validate(ValidationRequest request, String userId, String correlationId);

    BuildMessageResponse buildMessage(BuildMessageRequest request, String userId);
}