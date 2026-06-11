package com.verinite.profile.repository;

import com.verinite.profile.entity.MessageFormat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageFormatRepository extends JpaRepository<MessageFormat, Long> {

    List<MessageFormat> findAllByProfileIdAndDeletedAtIsNull(Long profileId);

    Optional<MessageFormat> findByIdAndDeletedAtIsNull(Long id);

    List<MessageFormat> findAllByDeletedAtIsNull();

    boolean existsByProfileIdAndFormatNameAndDeletedAtIsNull(Long profileId, String formatName);

    /**
     * Used by InternalProfileController — fetches active (non-deleted, status=active)
     * formats for a profile.  A profile may have multiple formats (different MTIs),
     * so we return a List and let the caller pick.
     */
    List<MessageFormat> findAllByProfileIdAndStatusAndDeletedAtIsNull(
            Long profileId, MessageFormat.Status status);
}