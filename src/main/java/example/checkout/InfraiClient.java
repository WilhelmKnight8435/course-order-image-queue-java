package example.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class InfraiClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String key;

    public InfraiClient(ObjectMapper json, @Value("${infrai.base-url}") String baseUrl,
                        @Value("${infrai.api-key}") String key) {
        this.json = json;
        this.baseUrl = baseUrl;
        this.key = key;
    }

    public JsonNode post(String path, Map<String, ?> body, String requestId) {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", requestId)
                    .method("POST", HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    long seconds = response.headers().firstValue("Retry-After")
                        .filter(s -> s.matches("[0-9]+"))
                        .map(Long::parseLong).orElse(1L << attempt);
                    Thread.sleep(Math.min(seconds, 30) * 1000);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    throw new ApiError(response.statusCode(), envelope.path("error"));
                }
                if (response.statusCode() >= 500) throw new IllegalStateException("Upstream HTTP " + response.statusCode());
                return envelope.path("data");
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read API response", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Request interrupted", e);
            }
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    public static class ApiError extends RuntimeException {
        public final int status;
        public ApiError(int status, JsonNode error) {
            super(error.path("code").asText("API_ERROR") + ": " + error.path("message").asText());
            this.status = status;
        }
    }
}
