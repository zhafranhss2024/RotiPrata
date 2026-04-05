package com.rotiprata.api.admin.service;

import java.util.UUID;

/**
 * Service interface for logging admin actions into audit logs.
 */
public interface AdminLoggingService {

    /**
     * Enum representing all possible admin actions.
     */
    enum AdminAction {
        // User Related
        SUSPEND_USER,
        UPDATE_USER_ROLE,
        UPDATE_USER_STATUS,
        RESET_USER_LESSON_PROGRESS,

        // Content Related
        APPROVE_CONTENT,
        UPDATE_CONTENT,
        REJECT_CONTENT,
        DELETE_CONTENT,

        // Flag Related
        TAKE_DOWN_CONTENT, // Take down content due to a flag
        RESOLVE_FLAG,
    }

    /**
     * Enum representing the type of target affected by the admin action.
     */
    enum TargetType {
        USER,
        CONTENT,
        FLAG,
    }

    /**
     * Logs an admin action into the audit_logs table.
     *
     * @param adminId     the ID of the admin performing the action
     * @param action      the type of action performed
     * @param targetId    the ID of the affected entity
     * @param targetType  the type of entity affected (USER, CONTENT, FLAG)
     * @param description additional details describing the action
     */
    void logAdminAction(
            UUID adminId,
            AdminAction action,
            UUID targetId,
            TargetType targetType,
            String description
    );
}