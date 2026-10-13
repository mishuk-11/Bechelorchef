package com.bachelorchef.repository;

import com.bachelorchef.entity.BulkUploadError;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkUploadErrorRepository extends JpaRepository<BulkUploadError, Long> {

    List<BulkUploadError> findByJobIdOrderByRowNumberAscIdAsc(Long jobId);
}
