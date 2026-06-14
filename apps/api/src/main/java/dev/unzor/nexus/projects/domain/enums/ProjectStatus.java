package dev.unzor.nexus.projects.domain.enums;

public enum ProjectStatus {
    /** El proyecto admite operaciones normales. */
    ACTIVE,

    /** El proyecto está bloqueado temporalmente. */
    SUSPENDED,

    /** El proyecto se conserva como histórico, pero ya no está operativo. */
    ARCHIVED
}
