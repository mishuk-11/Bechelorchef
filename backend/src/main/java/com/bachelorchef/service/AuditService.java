package com.bachelorchef.service;

import com.bachelorchef.dto.AdminDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.dto.Pagination;
import com.bachelorchef.entity.AuditLog;
import com.bachelorchef.entity.User;
import com.bachelorchef.repository.AuditLogRepository;
import com.bachelorchef.repository.UserRepository;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records sensitive admin actions. Callers must pass only safe metadata (never secrets). */
@Service
public class AuditService {

    private static final int DETAILS_MAX = 1000;

    private final AuditLogRepository logs;
    private final UserRepository users;

    public AuditService(AuditLogRepository logs, UserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    @Transactional
    public void record(Long actorId, String action, String targetType, Long targetId, String details) {
        String safe = details == null ? null : details.length() > DETAILS_MAX ? details.substring(0, DETAILS_MAX) : details;
        logs.save(new AuditLog(actorId, action, targetType, targetId, safe));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.AuditView> list(Integer page, Integer size) {
        Page<AuditLog> result = logs.findAllByOrderByCreatedAtDesc(Pagination.of(page, size, Sort.unsorted()));
        Set<Long> actorIds = result.getContent().stream().map(AuditLog::getActorId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> actors = users.findAllById(actorIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(result, log -> new AdminDtos.AuditView(log.getId(), log.getActorId(),
                actors.containsKey(log.getActorId()) ? actors.get(log.getActorId()).getEmail() : null,
                log.getAction(), log.getTargetType(), log.getTargetId(), log.getDetails(), log.getCreatedAt()));
    }
}
