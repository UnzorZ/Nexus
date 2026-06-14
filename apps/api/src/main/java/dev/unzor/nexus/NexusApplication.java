package dev.unzor.nexus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de la API de Nexus.
 *
 * <p>{@link EnableScheduling} habilita tareas periódicas acotadas, como la reentrega de
 * publicaciones de eventos incompletas de Spring Modulith
 * ({@code PanelSessionRevocationRepublisher}) que garantiza que la revocación de
 * sesiones desencadenada por cambios de estado de cuenta se complete incluso si Redis
 * falla justo después del commit.
 */
@EnableScheduling
@SpringBootApplication
public class NexusApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusApplication.class, args);
    }

}
