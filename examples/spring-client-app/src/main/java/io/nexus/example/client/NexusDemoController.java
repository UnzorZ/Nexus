package io.nexus.example.client;

import io.nexus.client.NexusClient;
import io.nexus.client.api.AuthorizationSnapshot;
import io.nexus.client.api.NotifyMessage;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.UUID;

/**
 * Demos de la mitad de <b>gestión</b> del starter, bajo la cadena cliente
 * (sesión OIDC, no bearer token). Muestra:
 * <ul>
 *   <li>{@code /admin/snapshot} — snapshot cacheado de permisos de un usuario
 *       (resolución fresca/autoritativa vía NexusClient, distinta del
 *       {@code @perm.has} por token).</li>
 *   <li>{@code /admin/notify} — envío de una notificación por Nexus.</li>
 *   <li>{@code /admin/config} — lectura de valores de configuración del proyecto
 *       ({@code nexus.config()}).</li>
 *   <li>{@code /admin/vault} — revelado de un secreto del vault
 *       ({@code nexus.vault()}).</li>
 *   <li>{@code /admin/metrics} — reporte push de un punto de métrica
 *       ({@code nexus.metrics()}; el lado pull Prometheus es un endpoint del
 *       backend).</li>
 * </ul>
 */
@Controller
@RequestMapping("/admin")
public class NexusDemoController {

    private final NexusClient nexus;

    public NexusDemoController(NexusClient nexus) {
        this.nexus = nexus;
    }

    @GetMapping("/snapshot")
    public String snapshot(@RequestParam(required = false) String userId, Model model) {
        Map<String, Object> result;
        if (userId != null && !userId.isBlank()) {
            try {
                AuthorizationSnapshot snap = nexus.permissions().snapshot(UUID.fromString(userId));
                boolean canCancel = nexus.permissions().can(UUID.fromString(userId), "orders.cancel");
                result = Map.of(
                        "snapshot", snap,
                        "canCancelOrders", canCancel,
                        "ok", true);
            } catch (RuntimeException e) {
                result = Map.of("ok", false, "error", e.getMessage());
            }
        } else {
            result = Map.of("ok", false, "error", "provide ?userId=<uuid>");
        }
        model.addAttribute("result", result);
        return "snapshot";
    }

    @PostMapping("/notify")
    public String notify(@RequestParam String to,
                         @RequestParam String subject,
                         @RequestParam String body,
                         Model model) {
        try {
            nexus.notifications().send(NotifyMessage.plain(to, subject, body));
            model.addAttribute("notifyResult", "Enviado a " + to);
        } catch (RuntimeException e) {
            model.addAttribute("notifyResult", "Error: " + e.getMessage());
        }
        return "redirect:/?notify=" + model.getAttribute("notifyResult");
    }

    @GetMapping("/config")
    public String config(@RequestParam(required = false) String key, Model model) {
        Map<String, Object> result;
        try {
            if (key != null && !key.isBlank()) {
                result = Map.of("ok", true, "value", nexus.config().get(key));
            } else {
                result = Map.of("ok", true, "values", nexus.config().list());
            }
        } catch (RuntimeException e) {
            result = Map.of("ok", false, "error", e.getMessage());
        }
        model.addAttribute("result", result);
        return "config";
    }

    @GetMapping("/vault")
    public String vault(@RequestParam(required = false) String key, Model model) {
        Map<String, Object> result;
        try {
            if (key != null && !key.isBlank()) {
                result = Map.of("ok", true, "secret", nexus.vault().get(key));
            } else {
                result = Map.of("ok", true, "secrets", nexus.vault().list());
            }
        } catch (RuntimeException e) {
            result = Map.of("ok", false, "error", e.getMessage());
        }
        model.addAttribute("result", result);
        return "vault";
    }

    @PostMapping("/metrics")
    public String metrics(@RequestParam String name,
                          @RequestParam double value,
                          @RequestParam(required = false) String tagKey,
                          @RequestParam(required = false) String tagValue,
                          Model model) {
        try {
            Map<String, String> tags = (tagKey != null && !tagKey.isBlank())
                    ? Map.of(tagKey, tagValue == null ? "" : tagValue)
                    : Map.of();
            model.addAttribute("result", Map.of("ok", true, "point", nexus.metrics().record(name, value, tags)));
        } catch (RuntimeException e) {
            model.addAttribute("result", Map.of("ok", false, "error", e.getMessage()));
        }
        return "metrics";
    }
}
