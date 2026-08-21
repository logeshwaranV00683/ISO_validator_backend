package com.verinite.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * A follow-up chat question asked from the Message Validator's AI
 * Explanation card. Carries the original validation context (so the
 * model can ground its answer in the actual message/errors) plus the
 * running conversation history and the new question.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    // Same context that produced the original explanation — lets the
    // model answer follow-ups without the client having to re-explain
    // what the validation was about.
    private String mti;
    private String profileName;
    private List<ValidationErrorDto> errors;
    private Map<Integer, String> parsedFields;
    private String originalExplanation;

    // Prior turns in this conversation, oldest first. Kept short on the
    // frontend (only this validation's session, not global chat history).
    private List<ChatTurn> history;

    @NotBlank(message = "Question must not be blank")
    private String question;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatTurn {
        private String role;   // "user" | "assistant"
        private String text;
    }
}