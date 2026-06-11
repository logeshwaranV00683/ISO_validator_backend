package com.verinite.profile.repository;

import com.verinite.profile.entity.MessageFormatVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageFormatVersionRepository extends JpaRepository<MessageFormatVersion, Long> {

    List<MessageFormatVersion> findAllByFormatIdOrderByVersionNumberDesc(Long formatId);

    Optional<MessageFormatVersion> findByFormatIdAndVersionNumber(Long formatId, Integer versionNumber);

    Optional<MessageFormatVersion> findByFormatIdAndIsCurrentTrue(Long formatId);

    /**
     * Clears is_current flag on ALL versions for a format before marking a new one current.
     * Called inside @Transactional service methods.
     */
    @Modifying
    @Transactional
    @Query("UPDATE MessageFormatVersion v SET v.isCurrent = false WHERE v.formatId = :formatId")
    void clearCurrentForFormat(@Param("formatId") Long formatId);
}