package dev.unzor.nexus.projects.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.unzor.nexus.projects.domain.enums.ProjectMembershipRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = false)
public record InviteMemberRequest(
        @NotBlank
        @Email
        String email,

        @NotNull
        @NonOwnerRole
        ProjectMembershipRole role
) {
}
