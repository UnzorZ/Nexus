package io.nexus.example.client;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Protected API demoing a <b>namespace-wildcard</b> permission. A token carrying
 * {@code inventory.*} satisfies {@code inventory.read} here; an exact
 * {@code inventory.read} grant works too.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryApiController {

    @GetMapping("/{sku}")
    @PreAuthorize("@perm.has(authentication, 'inventory.read')")
    public InventoryItem getItem(@PathVariable String sku) {
        return new InventoryItem(sku, 42, "AVAILABLE");
    }

    public record InventoryItem(String sku, int quantity, String status) {
    }
}
