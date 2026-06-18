package dev.unzor.nexus.projects.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateProjectRequest(
    @NotBlank
    @Size(max = 80)
    String slug,

    @NotBlank
    @Size(max = 120)
    String name,

    @Size(max = 1000)
    String description,

    @Size(max = 2048)
    String publicBaseUrl
) {
    public CreateProjectRequest {
        if (publicBaseUrl == null) {
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