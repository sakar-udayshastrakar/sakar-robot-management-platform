package com.sakarrobotics.cloud.iam;

/**
 * The authoritative permission set, verbatim from Master Requirements
 * Part 19 / SAKAR_SECURITY_REQUIREMENTS.md §4. Do not add ad-hoc
 * permissions outside this document without updating that spec first.
 */
public enum PermissionCode {
    ROBOT_VIEW,
    ROBOT_CONTROL,
    ROBOT_TASK_CREATE,
    ROBOT_TASK_CANCEL,
    ROBOT_LOCK,
    ROBOT_UNLOCK,
    ROBOT_CONFIGURE,
    ROBOT_DIAGNOSTICS,
    ROBOT_LOG_VIEW,
    AUDIT_VIEW,
    USER_MANAGE,
    ROLE_MANAGE,
    SYSTEM_ADMIN
}
