package dev.unzor.nexus.apikeys.security;

/**
 * El endpoint requiere la API key cruda ({@code X-Nexus-Api-Key}) pero la
 * request se autenticó con un instance token efímero (ADR-0012). Lo lanza
 * {@code /register}, que debe bootstrapar el token con la key larga y no
 * renovarse a partir de un token previo (de lo contrario un cliente con un
 * token podría renovar para siempre sin volver a presentar la key, eludiendo
 * rotación/revocación). Se traduce a 401 {@code raw_api_key_required}.
 */
public class RawApiKeyRequiredException extends RuntimeException {

    public RawApiKeyRequiredException() {
        super("This endpoint requires the raw API key (X-Nexus-Api-Key), not an instance token.");
    }
}
