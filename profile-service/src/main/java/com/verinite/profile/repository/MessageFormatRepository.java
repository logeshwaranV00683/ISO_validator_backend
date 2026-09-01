//package com.verinite.profile.repository;
//
//import com.verinite.profile.entity.MessageFormat;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.stereotype.Repository;
//import org.springframework.data.repository.query.Param;
//
//import java.util.List;
//import java.util.Optional;
//
//@Repository
//public interface MessageFormatRepository extends JpaRepository<MessageFormat, Long> {
//
//    List<MessageFormat> findAllByProfileIdAndDeletedAtIsNull(Long profileId);
//
//    Optional<MessageFormat> findByIdAndDeletedAtIsNull(Long id);
//
//    List<MessageFormat> findAllByDeletedAtIsNull();
//
//    boolean existsByProfileIdAndFormatNameAndDeletedAtIsNull(Long profileId, String formatName);
//
//    boolean existsByProfileIdAndMtiAndIdNotAndDeletedAtIsNull(Long profileId, String mti, Long excludeId);
//
//    /**
//     * Used by InternalProfileController — fetches active (non-deleted, status=active)
//     * formats for a profile.  A profile may have multiple formats (different MTIs),
//     * so we return a List and let the caller pick.
//     */
//    List<MessageFormat> findAllByProfileIdAndStatusAndDeletedAtIsNull(
//            Long profileId, MessageFormat.Status status);
//
//    @Query("SELECT DISTINCT f.mti FROM MessageFormat f " +
//            "WHERE f.profileId = :profileId AND f.deletedAt IS NULL " +
//            "AND f.mti IS NOT NULL ORDER BY f.mti")
//    List<String> findDistinctMtisByProfileId(@Param("profileId") Long profileId);
//}


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


    List<MessageFormat> findAllByProfileIdAndStatusAndDeletedAtIsNull(
            Long profileId, MessageFormat.Status status);


    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT f.mti FROM MessageFormat f " +
                    "WHERE f.profileId = :profileId AND f.deletedAt IS NULL AND f.mti IS NOT NULL " +
                    "ORDER BY f.mti"
    )
    List<String> findDistinctMtiByProfileId(@org.springframework.data.repository.query.Param("profileId") Long profileId);
}