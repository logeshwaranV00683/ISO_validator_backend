//package com.verinite.ai.service;
//
//import com.verinite.ai.client.OllamaClient;
//import com.verinite.ai.dto.AiChatRequest;
//import com.verinite.ai.dto.ValidationErrorDto;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.Map;
//
///**
// * Powers the follow-up chatbot inside the Message Validator's AI
// * Explanation card. Unlike OllamaService.getExplanation() (which runs
// * once per validation, off a template), this handles an open-ended,
// * multi-turn conversation the user drives themselves — so there's no
// * template/circuit-breaker plumbing here, just a grounded prompt built
// * fresh per turn.
// *
// * Ollama's /api/generate has no native multi-turn concept, so each call
// * re-sends the full context + trimmed history + new question as one
// * prompt. Kept deliberately simple (no CB, no DB run-logging) since this
// * is a lightweight, best-effort assistant feature, not the core
// * validation pipeline — a failure here should never look like anything
// * more than "chat is temporarily unavailable".
// */
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class AiChatService {
//
//    // Keep the prompt from growing unbounded in a long back-and-forth —
//    // only the most recent turns are needed for the model to stay
//    // coherent; older turns rarely change the answer to a new question.
//    private static final int MAX_HISTORY_TURNS = 6;
//
//    private final OllamaClient ollamaClient;
//
//    public String ask(AiChatRequest request) {
//        String prompt = buildPrompt(request);
//        try {
//            String response = ollamaClient.callOllama(prompt);
//            return response != null ? response.trim() : null;
//        } catch (Exception e) {
//            log.warn("[AI Chat] Ollama call failed: {}", e.getMessage());
//            return null; // caller returns a friendly "unavailable" message
//        }
//    }
//
//    private String buildPrompt(AiChatRequest req) {
//        StringBuilder sb = new StringBuilder();
//
//        sb.append("You are a friendly, helpful assistant explaining an ISO 8583 message validation ")
//                .append("result to someone who may not be deeply technical. Answer only using the context ")
//                .append("below. If the question is unrelated to this validation, say so briefly and warmly ")
//                .append("redirect the user back to the validation topic. ")
//                .append("Write like you're talking to a colleague: plain, everyday language, short sentences, ")
//                .append("no unexplained jargon. When you reference a DE (data element) number, briefly say ")
//                .append("what it means in plain terms rather than just citing the number. Keep answers concise ")
//                .append("— a few sentences is usually enough — and end with a clear, actionable takeaway when ")
//                .append("relevant (e.g. what the user should actually do or check next).\n\n");
//
//        sb.append("=== Validation Context ===\n");
//        if (req.getMti() != null) sb.append("MTI: ").append(req.getMti()).append("\n");
//        if (req.getProfileName() != null) sb.append("Profile: ").append(req.getProfileName()).append("\n");
//
//        List<ValidationErrorDto> errors = req.getErrors();
//        if (errors != null && !errors.isEmpty()) {
//            sb.append("Validation errors:\n");
//            for (ValidationErrorDto err : errors) {
//                sb.append("- DE").append(err.getDeNumber())
//                        .append(" (").append(err.getFieldName()).append("): ")
//                        .append(err.getErrorMessage())
//                        .append(" [severity=").append(err.getSeverity()).append("]\n");
//            }
//        }
//
//        Map<Integer, String> parsedFields = req.getParsedFields();
//        if (parsedFields != null && !parsedFields.isEmpty()) {
//            sb.append("Parsed fields (DE -> value):\n");
//            parsedFields.forEach((de, val) -> sb.append("- DE").append(de).append(": ").append(val).append("\n"));
//        }
//
//        if (req.getOriginalExplanation() != null && !req.getOriginalExplanation().isBlank()) {
//            sb.append("\nOriginal AI explanation already shown to the user:\n")
//                    .append(req.getOriginalExplanation()).append("\n");
//        }
//
//        List<AiChatRequest.ChatTurn> history = req.getHistory();
//        if (history != null && !history.isEmpty()) {
//            sb.append("\n=== Conversation so far ===\n");
//            int start = Math.max(0, history.size() - MAX_HISTORY_TURNS);
//            for (int i = start; i < history.size(); i++) {
//                AiChatRequest.ChatTurn turn = history.get(i);
//                String speaker = "assistant".equalsIgnoreCase(turn.getRole()) ? "Assistant" : "User";
//                sb.append(speaker).append(": ").append(turn.getText()).append("\n");
//            }
//        }
//
//        sb.append("\nUser: ").append(req.getQuestion()).append("\n");
//        sb.append("Assistant:");
//
//        return sb.toString();
//    }
//}

package com.verinite.ai.service;

import com.verinite.ai.client.OllamaClient;
import com.verinite.ai.dto.AiChatRequest;
import com.verinite.ai.dto.ValidationErrorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Powers the follow-up chatbot inside the Message Validator's AI
 * Explanation card. Unlike OllamaService.getExplanation() (which runs
 * once per validation, off a template), this handles an open-ended,
 * multi-turn conversation the user drives themselves — so there's no
 * template/circuit-breaker plumbing here, just a grounded prompt built
 * fresh per turn.
 *
 * Ollama's /api/generate has no native multi-turn concept, so each call
 * re-sends the full context + trimmed history + new question as one
 * prompt. Kept deliberately simple (no CB, no DB run-logging) since this
 * is a lightweight, best-effort assistant feature, not the core
 * validation pipeline — a failure here should never look like anything
 * more than "chat is temporarily unavailable".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    // Keep the prompt from growing unbounded in a long back-and-forth —
    // only the most recent turns are needed for the model to stay
    // coherent; older turns rarely change the answer to a new question.
    // Kept small (not just for prompt size, but because a shorter prompt
    // means less input for the model to process before it can start
    // generating, which matters on CPU).
    private static final int MAX_HISTORY_TURNS = 4;

    private final OllamaClient ollamaClient;

    // Chat answers are short and conversational by design (unlike BRD
    // extraction's long structured JSON) — capping num_predict well below
    // the shared ollama.max.tokens default (1024) directly cuts CPU
    // generation time, since Ollama scales roughly linearly with tokens
    // produced. 300 tokens is comfortably enough for a few friendly
    // sentences plus a takeaway, per the prompt's own instructions.
    private static final int CHAT_MAX_TOKENS = 300;

    public String ask(AiChatRequest request) {
        String prompt = buildPrompt(request);
        try {
            String response = ollamaClient.callOllama(prompt, CHAT_MAX_TOKENS);
            return response != null ? response.trim() : null;
        } catch (Exception e) {
            log.warn("[AI Chat] Ollama call failed: {}", e.getMessage());
            return null; // caller returns a friendly "unavailable" message
        }
    }

    private String buildPrompt(AiChatRequest req) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are a friendly, helpful assistant explaining an ISO 8583 message validation ")
                .append("result to someone who may not be deeply technical. Answer only using the context ")
                .append("below. If the question is unrelated to this validation, say so briefly and warmly ")
                .append("redirect the user back to the validation topic. ")
                .append("Write like you're talking to a colleague: plain, everyday language, short sentences, ")
                .append("no unexplained jargon. When you reference a DE (data element) number, briefly say ")
                .append("what it means in plain terms rather than just citing the number. Keep answers concise ")
                .append("— a few sentences is usually enough — and end with a clear, actionable takeaway when ")
                .append("relevant (e.g. what the user should actually do or check next).\n\n");

        sb.append("=== Validation Context ===\n");
        if (req.getMti() != null) sb.append("MTI: ").append(req.getMti()).append("\n");
        if (req.getProfileName() != null) sb.append("Profile: ").append(req.getProfileName()).append("\n");

        List<ValidationErrorDto> errors = req.getErrors();
        if (errors != null && !errors.isEmpty()) {
            sb.append("Validation errors:\n");
            for (ValidationErrorDto err : errors) {
                sb.append("- DE").append(err.getDeNumber())
                        .append(" (").append(err.getFieldName()).append("): ")
                        .append(err.getErrorMessage())
                        .append(" [severity=").append(err.getSeverity()).append("]\n");
            }
        }

        Map<Integer, String> parsedFields = req.getParsedFields();
        if (parsedFields != null && !parsedFields.isEmpty()) {
            sb.append("Parsed fields (DE -> value):\n");
            parsedFields.forEach((de, val) -> sb.append("- DE").append(de).append(": ").append(val).append("\n"));
        }

        if (req.getOriginalExplanation() != null && !req.getOriginalExplanation().isBlank()) {
            sb.append("\nOriginal AI explanation already shown to the user:\n")
                    .append(req.getOriginalExplanation()).append("\n");
        }

        List<AiChatRequest.ChatTurn> history = req.getHistory();
        if (history != null && !history.isEmpty()) {
            sb.append("\n=== Conversation so far ===\n");
            int start = Math.max(0, history.size() - MAX_HISTORY_TURNS);
            for (int i = start; i < history.size(); i++) {
                AiChatRequest.ChatTurn turn = history.get(i);
                String speaker = "assistant".equalsIgnoreCase(turn.getRole()) ? "Assistant" : "User";
                sb.append(speaker).append(": ").append(turn.getText()).append("\n");
            }
        }

        sb.append("\nUser: ").append(req.getQuestion()).append("\n");
        sb.append("Assistant:");

        return sb.toString();
    }
}