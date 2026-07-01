package dev.unzor.nexus.notify.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record NotificationTemplateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 20000) String bodyTemplate
) {
}
