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
            String mti, LocalDate dateFrom, LocalDate dateTo,
            String search) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

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


            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("runReference")), like),
                        cb.like(cb.lower(root.get("mti")), like)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}