package com.bachelorchef.repository;

import com.bachelorchef.entity.ContentReport;
import com.bachelorchef.entity.ReportStatus;
import com.bachelorchef.entity.ReportTargetType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(Long reporterId, ReportTargetType targetType,
                                                                Long targetId, ReportStatus status);

    @EntityGraph(attributePaths = "reporter")
    Page<ContentReport> findByStatus(ReportStatus status, Pageable pageable);

    List<ContentReport> findByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, Long targetId, ReportStatus status);

    long countByStatus(ReportStatus status);
}
