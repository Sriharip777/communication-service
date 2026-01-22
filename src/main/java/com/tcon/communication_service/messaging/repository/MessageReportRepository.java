package com.tcon.communication_service.messaging.repository;

import com.tcon.communication_service.messaging.entity.MessageReport;
import com.tcon.communication_service.messaging.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Message Report Repository
 * Data access layer for message reports
 */
@Repository
public interface MessageReportRepository extends MongoRepository<MessageReport, String> {

    Page<MessageReport> findByStatus(ReportStatus status, Pageable pageable);

    List<MessageReport> findByMessageId(String messageId);

    long countByMessageIdAndStatus(String messageId, ReportStatus status);

    Page<MessageReport> findByReportedBy(String reportedBy, Pageable pageable);

    boolean existsByMessageIdAndReportedBy(String messageId, String reportedBy);
}

