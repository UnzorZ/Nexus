-- Normaliza client_secret_hash de project_oauth_clients EXISTENTES.
--
-- El código anterior guardaba "{bcrypt}" + hash (formato DelegatingPasswordEncoder),
-- pero el bean PasswordEncoder es un BCryptPasswordEncoder plano, así que SAS
-- rechazaba esos clientes en el token endpoint ("Encoded password does not look
-- like BCrypt" -> invalid_client) hasta rotar cada secreto a mano. Esta migración
-- quita el prefijo de las filas existentes; el hash bcrypt subyacente es exactamente
-- el que produce BCryptPasswordEncoder (el prefijo era un literal añadido, no parte
-- del cómputo bcrypt), así que la verificación pasa sin rotar.
--
-- El cliente técnico global oauth2_registered_client no se toca: se reconcilia solo
-- al arranque (OidcRegisteredClientBootstrap) y ya re-hashea sin prefijo si no matchea.
UPDATE project_oauth_clients
SET client_secret_hash = substring(client_secret_hash from 9)
WHERE client_secret_hash LIKE '{bcrypt}%';
