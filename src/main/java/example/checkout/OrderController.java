package example.checkout;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
public class OrderController {
    private final OrderImageService service;
    public OrderController(OrderImageService service) { this.service = service; }

    @PostMapping("/orders")
    public ReceiptDecision.Receipt checkout(@RequestBody ReceiptDecision.Order order) { return service.checkout(order); }

    @PostMapping("/fulfillment/run")
    public List<ReceiptDecision.Receipt> fulfill() { return service.fulfill(); }

    @GetMapping("/orders/{id}/receipt")
    public ResponseEntity<ReceiptDecision.Receipt> receipt(@PathVariable String id) {
        ReceiptDecision.Receipt receipt = service.receipt(id);
        return receipt == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(receipt);
    }

    @ExceptionHandler(InfraiClient.ApiError.class)
    public ResponseEntity<Map<String, String>> rejected(InfraiClient.ApiError error) {
        int status = error.status >= 400 && error.status < 500 ? error.status : 502;
        return ResponseEntity.status(status).body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
    }
}
