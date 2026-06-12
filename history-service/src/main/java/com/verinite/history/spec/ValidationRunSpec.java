package com.verinite.history.spec;

import com.verinite.common.enums.RunStatus;
import com.verinite.history.entity.ValidationRun;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ValidationRunSpec {

    private ValidationRunSpec() {}

    public static Specification<ValidationRun> filter(
            Long profileId, Long userId, String status,
            String mti, LocalDate dateFrom, LocalDate dateTo) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // FIX Bug 2: was cb.equal(root.get("isDeleted"), false)
            // Entity has no isDeleted field — soft delete uses deletedAt timestamp
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (profileId != null)
                predicates.add(cb.equal(root.get("profileId"), profileId));
            if (userId != null)
                predicates.add(cb.equal(root.get("userId"), userId));
            if (status != null && !status.isBlank())
                predicates.add(cb.equal(root.get("status"),
                        RunStatus.valueOf(status.toUpperCase())));
            if (mti != null && !mti.isBlank())
                predicates.add(cb.equal(root.get("mti"), mti));
            if (dateFrom != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom.atStartOfDay()));
            if (dateTo != null)
                predicates.add(cb.lessThan(root.get("createdAt"), dateTo.plusDays(1).atStartOfDay()));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}