package com.verinite.profile.repository;

import com.verinite.profile.entity.MessageFormatVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageFormatVersionRepository extends JpaRepository<MessageFormatVersion, Long> {

    List<MessageFormatVersion> findAllByFormatIdOrderByVersionNumberDesc(Long formatId);

    Optional<MessageFormatVersion> findByFormatIdAndVersionNumber(Long formatId, Integer versionNumber);
}