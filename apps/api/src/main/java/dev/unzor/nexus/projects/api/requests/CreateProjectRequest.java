package dev.unzor.nexus.projects.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateProjectRequest(
    @NotBlank
    @Size(max = 80)
    @Pattern(
        regexp = "^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$",
        message = "must contain only lowercase letters, numbers and hyphens; it must not start or end with a hyphen"
    )
    String slug,

    @NotBlank
    @Size(max = 120)
    String name,

    @Size(max = 1000)
    String description,

    @Size(max = 2048)
    @HttpsUrl
    String publicBaseUrl
) {
    public CreateProjectRequest {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            publicBaseUrl = "https://" + slug + ".nexus.local";
        }
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

}
