-- OIDC Back-Channel Logout (RFC 8417): URI del cliente a la que Nexus POSTea un logout
-- token firmado cuando la sesión del usuario final termina, para que el RP invalide su
-- sesión local sin depender del browser (front-channel). Nullable: sólo los clientes que
-- soporten back-channel logout lo declaran.
ALTER TABLE project_oauth_clients ADD COLUMN backchannel_logout_uri VARCHAR(500);
