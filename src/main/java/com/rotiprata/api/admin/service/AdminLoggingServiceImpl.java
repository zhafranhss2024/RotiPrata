package com.rotiprata.api.admin.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Implementation of AdminLoggingService.
 * Responsible for persisting admin actions into Supabase audit_logs table.
 */
@Service
public class AdminLoggingServiceImpl implements AdminLoggingService {

    private final SupabaseAdminRestClient supabaseAdminRestClient; 
    private static final Logger log = LoggerFactory.getLogger(AdminLoggingServiceImpl.class);
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    // Constant
    private static final String ADMIN_ID = "admin_id";
    private static final String ACTION = "action";
    private static final String TARGET_ID = "target_id";
    private static final String TARGET_TYPE = "target_type";
    private static final String DESCRIPTION = "description";
    private static final String CREATED_AT = "created_at";

    /**
     * Constructor for dependency injection.
     *
     * @param supabaseAdminRestClient client used to interact with Supabase
     */
    public AdminLoggingServiceImpl(SupabaseAdminRestClient supabaseAdminRestClient) {
        this.supabaseAdminRestClient = supabaseAdminRestClient;
    }

    /**
     * Creates and sends an audit log entry to Supabase.
     *
     * Builds a log entry containing admin action details and current timestamp,
     * then inserts it into the "audit_logs" table.
     * Best-effort Logging
     */
    @Override
    public void logAdminAction(
            UUID adminId,
            AdminAction action,
            UUID targetId,
            TargetType targetType,
            String description
    ) {
        Map<String, Object> logEntry = Map.of(
            ADMIN_ID, adminId,
            ACTION, action.name(),
            TARGET_ID, targetId,
            TARGET_TYPE, targetType.name(),
            DESCRIPTION, description,
            CREATED_AT, OffsetDateTime.now()
        );

        List<Map<String, Object>> rows = List.of(logEntry);

        try {
            supabaseAdminRestClient.postList("audit_logs", rows, MAP_LIST);
        } catch (Exception e) {
            log.error("Failed to log admin action: {} - {}", action.name(), e.getMessage(), e);
        }
        
    }
}