package com.verinite.auth_service.repository;

import com.verinite.auth_service.entity.User;
import com.verinite.common.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByUsername(String username);
    List<User> findByDeletedAtIsNull();

    Page<User> findByDeletedAtIsNull(Pageable pageable);
    Page<User> findByDeletedAtIsNullAndRole(Role role, Pageable pageable);
}