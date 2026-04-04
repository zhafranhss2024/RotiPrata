package com.rotiprata.api.admin.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

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
                "admin_id", adminId,
                "action", action.name(),
                "target_id", targetId,
                "target_type", targetType.name(),
                "description", description,
                "created_at", OffsetDateTime.now()
        );

        List<Map<String, Object>> rows = List.of(logEntry);

        supabaseAdminRestClient.postList("audit_logs", rows, MAP_LIST);
    }
}