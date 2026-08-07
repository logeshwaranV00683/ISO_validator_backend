package com.verinite.history.spec;

import com.verinite.history.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AuditLogSpec {

    private AuditLogSpec() {}

    public static Specification<AuditLog> filter(
            String action,
            String entityType,
            String entityId,
            String sourceService,
            Long userId,
            LocalDate dateFrom,
            LocalDate dateTo) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null && !entityId.isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (sourceService != null && !sourceService.isBlank()) {
                predicates.add(cb.equal(root.get("sourceService"), sourceService));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"), dateFrom.atStartOfDay()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(
                        root.get("createdAt"), dateTo.plusDays(1).atStartOfDay()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
