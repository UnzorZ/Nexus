package dev.unzor.nexus.notify.api.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/** Cuerpo del guardado de variables globales ({@code PUT .../notify/variables}). */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SaveGlobalVariablesRequest(
        Map<String, String> variables
) {
}
