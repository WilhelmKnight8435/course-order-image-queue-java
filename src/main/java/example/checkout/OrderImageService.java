package example.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderImageService {
    private final InfraiClient client;
    private final String queue;
    private final int maxMessages;
    private final int visibilityTimeout;
    private final Map<String, ReceiptDecision.Receipt> receipts = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> processed = new ConcurrentHashMap<>();

    public OrderImageService(InfraiClient client,
                             @Value("${checkout.queue.name}") String queue,
                             @Value("${checkout.queue.max-messages}") int maxMessages,
                             @Value("${checkout.queue.visibility-timeout}") int visibilityTimeout) {
        this.client = client;
        this.queue = queue;
        this.maxMessages = maxMessages;
        this.visibilityTimeout = visibilityTimeout;
    }

    public ReceiptDecision.Receipt checkout(ReceiptDecision.Order order) {
        ReceiptDecision.Receipt receipt = ReceiptDecision.checkout(order);
        if (order.paid()) {
            for (int i = 0; i < order.images().size(); i++) {
                client.post("/v1/queue/publish", Map.of("queue", queue, "payload", Map.of(
                    "orderId", order.orderId(), "image", order.images().get(i), "index", i)), order.orderId() + ":queue:" + i);
            }
        }
        receipts.put(order.orderId(), receipt);
        return receipt;
    }

    public List<ReceiptDecision.Receipt> fulfill() {
        JsonNode items = client.post("/v1/queue/consume", Map.of(
            "queue", queue, "max_messages", maxMessages, "visibility_timeout", visibilityTimeout),
            "consume:" + System.currentTimeMillis()).path("items");
        List<ReceiptDecision.Receipt> updates = new ArrayList<>();
        for (JsonNode item : items) {
            JsonNode payload = item.path("payload");
            String orderId = payload.path("orderId").asText();
            ReceiptDecision.Receipt previous = receipts.get(orderId);
            if (previous == null || !previous.state().equals("AWAITING_IMAGES")) continue;
            String imageKey = orderId + ":" + payload.path("index").asInt();
            Set<String> done = processed.computeIfAbsent(orderId, ignored -> ConcurrentHashMap.newKeySet());
            if (done.contains(imageKey)) continue;
            client.post("/v1/image/resize", Map.of("image", payload.path("image").asText(),
                "width", 1200, "height", 1200, "fit", "contain", "store", true),
                imageKey + ":resize");
            done.add(imageKey);
            if (done.size() < previous.imageCount()) continue;
            ReceiptDecision.Receipt updated = new ReceiptDecision.Receipt(orderId,
                previous.customerEmail(), "FULFILLED", previous.imageCount());
            receipts.put(orderId, updated);
            updates.add(updated);
        }
        return updates;
    }

    public ReceiptDecision.Receipt receipt(String id) { return receipts.get(id); }
}
