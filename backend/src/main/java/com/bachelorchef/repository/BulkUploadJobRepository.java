package com.bachelorchef.repository;

import com.bachelorchef.entity.BulkUploadJob;
import com.bachelorchef.entity.ImportStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BulkUploadJobRepository extends JpaRepository<BulkUploadJob, Long> {

    @EntityGraph(attributePaths = "uploadedBy")
    @Query("select j from BulkUploadJob j")
    Page<BulkUploadJob> findAllWithUploader(Pageable pageable);

    @EntityGraph(attributePaths = "uploadedBy")
    @Query("select j from BulkUploadJob j where j.id = :id")
    Optional<BulkUploadJob> findWithUploader(@Param("id") Long id);

    /**
     * Atomically claims a previewed job for processing. Returns 1 for exactly one caller, which
     * makes confirmation idempotent under retries and concurrent double-clicks.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update BulkUploadJob j set j.status = com.bachelorchef.entity.ImportStatus.PROCESSING, j.version = j.version + 1
            where j.id = :id and j.status = com.bachelorchef.entity.ImportStatus.PREVIEWED
            """)
    int claimForProcessing(@Param("id") Long id);

    List<BulkUploadJob> findByStatusAndStartedAtBefore(ImportStatus status, Instant cutoff);
}
