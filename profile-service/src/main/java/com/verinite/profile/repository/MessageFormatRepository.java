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

    Optional<MessageFormat> findByProfileIdAndDeletedAtIsNull(Long profileId);
}