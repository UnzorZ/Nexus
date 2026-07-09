package io.nexus.example.client;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Protected API demoing <b>permission-key authorization</b> from the token claim.
 * {@code GET /api/orders} requires the {@code orders.read} permission. Because the
 * {@code permissions} claim carries wildcards verbatim, a token with
 * {@code permissions:["orders.*"]} (or {@code ["*"]}) satisfies it — the match is
 * resolved by the starter's {@code @perm} bean ({@code NexusPermissionService}) via
 * the {@code @perm.has(...)} SpEL expression.
 */
@RestController
@RequestMapping("/api/orders")
public class OrdersApiController {

    @GetMapping
    @PreAuthorize("@perm.has(authentication, 'orders.read')")
    public List<OrderSummary> listOrders() {
        // Demo data — in a real app this would be your orders store.
        return List.of(
                new OrderSummary(1001L, "alice@example.com", "PAID"),
                new OrderSummary(1002L, "bob@example.com", "SHIPPED"),
                new OrderSummary(1003L, "carol@example.com", "PENDING"));
    }

    public record OrderSummary(Long id, String customer, String status) {
    }
}
