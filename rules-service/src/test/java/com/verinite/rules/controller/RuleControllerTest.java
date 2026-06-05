package com.verinite.rules.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verinite.common.enums.DataType;
import com.verinite.common.enums.Severity;
import com.verinite.rules.config.SecurityConfig;
import com.verinite.rules.dto.RuleDto;
import com.verinite.rules.entity.ValidationRule;
import com.verinite.rules.event.RuleEventPublisher;
import com.verinite.rules.service.RuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuleController.class)
@Import(SecurityConfig.class)   // activates HeaderAuthenticationFilter + @EnableMethodSecurity
class RuleControllerTest {

    @Autowired MockMvc      mvc;
    @Autowired ObjectMapper json;

    @MockBean
    RuleService        ruleService;
    @MockBean RuleEventPublisher eventPublisher;

    private RuleDto     dto;
    private static final String BODY = """
            {"profileId":1,"profileName":"Visa","mti":"0200",
             "deNumber":"DE7","fieldName":"Tx Date","dataType":"numeric"}
            """;

    @BeforeEach
    void setUp() {
        dto = RuleDto.builder()
                .id(1L).profileId(1L).mti("0200")
                .deNumber("DE7").fieldName("Tx Date")
                .dataType(DataType.valueOf("numeric")).severity(Severity.valueOf("CRITICAL"))
                .priority(1).active(true).build();
    }

    // ── Scenario 1: Full CRUD via ADMIN JWT ───────────────────────────

    @Test
    void getEffectiveRules_noAuthRequired_200() throws Exception {
        when(ruleService.getEffectiveRules(1L, "0200")).thenReturn(List.of(dto));
        mvc.perform(get("/rules").param("profileId", "1").param("mti", "0200"))
                .andExpect(status().isOk());
    }

    @Test
    void createRule_adminJwt_201() throws Exception {
        when(ruleService.create(any())).thenReturn(dto);
        mvc.perform(post("/rules")
                        .header("X-Auth-User-Id", "1")
                        .header("X-Auth-Username", "admin")
                        .header("X-Auth-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void updateRule_adminJwt_200() throws Exception {
        when(ruleService.update(eq(1L), any())).thenReturn(dto);
        mvc.perform(put("/rules/1")
                        .header("X-Auth-User-Id", "1")
                        .header("X-Auth-Username", "admin")
                        .header("X-Auth-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void toggleStatus_adminJwt_200() throws Exception {
        when(ruleService.toggleStatus(1L)).thenReturn(dto);
        mvc.perform(patch("/rules/1/status")
                        .header("X-Auth-User-Id", "1")
                        .header("X-Auth-Username", "admin")
                        .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteRule_adminJwt_204() throws Exception {
        when(ruleService.getById(1L)).thenReturn(
                ValidationRule.builder().id(1L).profileId(1L).mti("0200").build());
        doNothing().when(ruleService).softDelete(1L);
        mvc.perform(delete("/rules/1")
                        .header("X-Auth-User-Id", "1")
                        .header("X-Auth-Username", "admin")
                        .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isNoContent());
    }

    // ── Scenario 2: VIEWER JWT → 403 ──────────────────────────────────

    @Test
    void createRule_viewerJwt_403() throws Exception {
        mvc.perform(post("/rules")
                        .header("X-Auth-User-Id", "2")
                        .header("X-Auth-Username", "viewer")
                        .header("X-Auth-Role", "VIEWER")    // HeaderAuthenticationFilter sets ROLE_VIEWER
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());       // @PreAuthorize("hasRole('ADMIN')") blocks it

        verifyNoInteractions(ruleService);           // service must never be touched
    }

    // ── Scenario 3: RULE_UPDATED event end-to-end trace ───────────────

    @Test
    void createRule_publishesRuleUpdatedWithCorrectArgs() throws Exception {
        when(ruleService.create(any())).thenReturn(dto);   // dto has profileId=1, mti="0200"
        mvc.perform(post("/rules")
                        .header("X-Auth-User-Id", "1")
                        .header("X-Auth-Username", "admin")
                        .header("X-Auth-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());
        verify(eventPublisher).publishRuleUpdated(1L, "0200");
    }

    @Test
    void updateRule_publishesRuleUpdated() throws Exception {
        when(ruleService.update(eq(1L), any())).thenReturn(dto);
        mvc.perform(put("/rules/1")
                        .header("X-Auth-User-Id", "1")
                        .header("X-Auth-Username", "admin")
                        .header("X-Auth-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
        verify(eventPublisher).publishRuleUpdated(1L, "0200");
    }

    @Test
    void deleteRule_publishesRuleDeleted() throws Exception {
        when(ruleService.getById(1L)).thenReturn(
                ValidationRule.builder().id(1L).profileId(1L).mti("0200").build());
        doNothing().when(ruleService).softDelete(1L);
        mvc.perform(delete("/rules/1")
                        .header("X-Auth-User-Id", "1")
                        .header("X-Auth-Username", "admin")
                        .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isNoContent());
        verify(eventPublisher).publishRuleDeleted(1L, "0200");
    }
}