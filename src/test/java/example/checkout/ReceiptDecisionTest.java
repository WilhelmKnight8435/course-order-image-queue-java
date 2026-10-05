package example.checkout;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceiptDecisionTest {
    @Test
    void unpaidOrderDoesNotEnterImageFulfillment() {
        var order = new ReceiptDecision.Order("course-42", "student@example.org", false, List.of("image-1"));
        assertEquals("PAYMENT_REQUIRED", ReceiptDecision.checkout(order).state());
    }

    @Test
    void paidOrderEntersImageFulfillment() {
        var order = new ReceiptDecision.Order("course-42", "student@example.org", true, List.of("image-1", "image-2"));
        assertEquals("AWAITING_IMAGES", ReceiptDecision.checkout(order).state());
        assertEquals(2, ReceiptDecision.checkout(order).imageCount());
    }
}
