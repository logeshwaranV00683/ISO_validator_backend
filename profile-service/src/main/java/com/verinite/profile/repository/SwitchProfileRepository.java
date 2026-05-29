package com.verinite.profile.repository;

import com.verinite.profile.entity.SwitchProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SwitchProfileRepository extends JpaRepository<SwitchProfile, Long> {

    List<SwitchProfile> findAllByDeletedFalse();

    Optional<SwitchProfile> findByIdAndDeletedFalse(Long id);

    boolean existsByProfileName(String profileName);
}