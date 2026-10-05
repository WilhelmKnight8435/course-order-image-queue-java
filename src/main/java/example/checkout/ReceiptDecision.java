package example.checkout;

import java.util.List;

public final class ReceiptDecision {
    private ReceiptDecision() {}

    public record Order(String orderId, String customerEmail, boolean paid, List<String> images) {}
    public record Receipt(String orderId, String customerEmail, String state, int imageCount) {}

    public static Receipt checkout(Order order) {
        if (order.orderId() == null || order.orderId().isBlank() || order.customerEmail() == null
            || order.customerEmail().isBlank() || order.images() == null || order.images().isEmpty()) {
            throw new IllegalArgumentException("Order ID, customer email and images are required");
        }
        return new Receipt(order.orderId(), order.customerEmail(),
            order.paid() ? "AWAITING_IMAGES" : "PAYMENT_REQUIRED", order.images().size());
    }
}
