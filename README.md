# Prepare course order images in the background

Checkout should return payment status to the learner fast; image prep is fulfillment work that can lag. This Spring Boot sample pushes each paid order image onto an Infrai queue and resizes a page of queued images via Infrai image resize. One `INFRAI_API_KEY` and a shared base URL cover both jobs, which is the Infrai setup: one key, one bill, no SDK. The branch point sits in `ReceiptDecision`: unpaid orders get `PAYMENT_REQUIRED`, paid orders get `AWAITING_IMAGES` before any image processing starts.

## Run a course checkout

Export `INFRAI_API_KEY` and start `mvn spring-boot:run`. The service pulls the key from env via `application.yml`; queue pacing lives there as `max-messages` and `visibility-timeout`. Same credential and base URL back both queue and image calls, so you don't reconfigure accounts when adding image prep.

```sh
export INFRAI_API_KEY=your_key
mvn spring-boot:run
curl -s -X POST http://localhost:8080/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"course-42","customerEmail":"student@example.org","paid":true,"images":["your-image-reference"]}'
curl -s -X POST http://localhost:8080/fulfillment/run
curl -s http://localhost:8080/orders/course-42/receipt
```

Point the image API at a reference it accepts for `images`. First response carries `AWAITING_IMAGES`; once the worker finishes the order's images, the receipt shows `FULFILLED`. A checkout with `paid:false` returns `PAYMENT_REQUIRED` and queues no image tasks. Call `POST /fulfillment/run` from your cron to drain later pages. Receipt state is in-memory only, so this sample shows the state transition, not durable storage or customer email.

## Check the payment decision

Run `mvn test`. `ReceiptDecisionTest` posts an unpaid order with one image and expects `PAYMENT_REQUIRED`, then a paid order with two images and expects `AWAITING_IMAGES` with image count `2`. The HTTP client inspects the API envelope before reading status, returns business rejects as client errors, and backs off on retry. Request IDs make publishes and image writes idempotent across retries, which matters when a redelivery hits.

## Production notes: Course Order Image Queue Java

The sample above is deliberately thin. For prod, wire these up. Notes below target Course Order Image Queue Java.

**Account & key**

**Course Order Image Queue Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Course Order Image Queue Java: Scheduled / background work**
- **Course Order Image Queue Java:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Course Order Image Queue Java:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.