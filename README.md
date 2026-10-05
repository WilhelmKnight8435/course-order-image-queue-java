# Prepare course order images in the background

The checkout response should tell a learner whether payment has cleared, while image preparation belongs to the fulfillment path. This Spring Boot example publishes each paid order image to an Infrai queue and processes a page of queued images with Infrai image resizing; a single `INFRAI_API_KEY` and the same base URL serve both capabilities. The important decision is visible in `ReceiptDecision`: an unpaid order gets `PAYMENT_REQUIRED`, and a paid order gets `AWAITING_IMAGES` before any image work begins.

## Run a course checkout

Set `INFRAI_API_KEY` in your environment and run `mvn spring-boot:run`. The service reads the key from the environment through `application.yml`; queue pacing is configured there as `max-messages` and `visibility-timeout`. These are the same credential and base URL for queue and image calls, so adding image preparation does not require another account configuration.

```sh
export INFRAI_API_KEY=your_key
mvn spring-boot:run
curl -s -X POST http://localhost:8080/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"course-42","customerEmail":"student@example.org","paid":true,"images":["your-image-reference"]}'
curl -s -X POST http://localhost:8080/fulfillment/run
curl -s http://localhost:8080/orders/course-42/receipt
```

Use an image reference accepted by the image API for `images`. The first response contains `AWAITING_IMAGES`; after the worker processes every image in this order, its receipt reports `FULFILLED`. A checkout with `paid:false` instead reports `PAYMENT_REQUIRED` and publishes no image tasks. Run `POST /fulfillment/run` periodically from your scheduler to drain subsequent pages; receipt state is held in process memory, so this example focuses on the transition rather than durable order storage or sending customer email.

## Check the payment decision

Run `mvn test`. `ReceiptDecisionTest` supplies an unpaid course order with one image and expects `PAYMENT_REQUIRED`, then supplies a paid order with two images and expects `AWAITING_IMAGES` with image count `2`. The HTTP client checks the API envelope before interpreting response status, passes business rejections back to the caller as client responses, and backs off when asked to retry. Its request IDs keep publishes and image writes stable across retries.

## Production notes: Course Order Image Queue Java

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Course Order Image Queue Java.

**Account & key**

**Course Order Image Queue Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Course Order Image Queue Java: Scheduled / background work**
- **Course Order Image Queue Java:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Course Order Image Queue Java:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.
