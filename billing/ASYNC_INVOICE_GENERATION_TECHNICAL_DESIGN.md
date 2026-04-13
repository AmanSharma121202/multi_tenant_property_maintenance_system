# Asynchronous Invoice Generation - Technical Design & Implementation

## Scope and Audience
This document explains how invoice generation was moved from a synchronous API flow to an asynchronous background-processing model.

**Audience:** Backend engineers, QA engineers, tech leads, and product stakeholders.

---

## 1) Problem Statement - Previous Approach

### Previous implementation (synchronous/blocking)
Earlier, invoice generation executed fully inside the API request thread.

- Endpoint: `POST /tenants/{tenantId}/invoices:generate`
- Controller called `InvoiceService.generate(...)` directly.
- Request completed only after invoice computation + persistence finished.

### Old flow (high-level)

```text
Client -> InvoiceController (:generate)
       -> InvoiceService.generate(...)
       -> DB read/write (unit/profile/previous invoice/new invoice)
       -> return 201 + Invoice body
```

### Issues observed

- **Performance bottleneck:** API thread stayed busy for full generation logic.
- **User wait time:** User had to wait for full processing before any response.
- **Timeout risk:** Under load, long-running operations could increase timeout probability.
- **Scalability limits:** Throughput was constrained by request thread availability.
- **Reliability/UX impact:** Retries from users could overlap with processing windows and increase operational stress.

---

## 2) Solution Overview - Asynchronous Invoice Generation

### Why async
Async processing was chosen to:

- Return fast acknowledgment to users (`202 Accepted`),
- Decouple request handling from heavy processing,
- Improve concurrency and resilience under load,
- Provide explicit progress tracking (`SUBMITTED`, `RUNNING`, `SUCCEEDED`, `FAILED`).

### New high-level architecture

```text
+--------+      +----------------------+      +-----------------------------+
| Client | ---> | Invoice API          | ---> | InvoiceGenerationJob store  |
|        |      | submit + status      |      | (Couchbase collection)      |
+--------+      +----------------------+      +-----------------------------+
                     |                                    ^
                     v                                    |
              +----------------------+                    |
              | Async service        |--------------------+
              | CompletableFuture +  |   status updates
              | FutureTask + Executor|
              +----------------------+
                     |
                     v
              +----------------------+
              | InvoiceService.generate|
              | (existing core logic) |
              +----------------------+
```

### Key components in current implementation

- **API layer:** `InvoiceController`
  - `POST /tenants/{tenantId}/invoices:generate` -> submit async job
  - `GET /tenants/{tenantId}/invoices/jobs/{jobId}` -> get job status
- **Async orchestrator:** `AsyncInvoiceGenerationService`
- **Threading mechanism:** `CompletableFuture` + `FutureTask` + `ThreadPoolTaskExecutor`
- **Persistence:** `InvoiceGenerationJob` in Couchbase collection `invoice_generation_jobs`
- **Status contract:** `InvoiceGenerationJobResponse`
- **Retention:** `InvoiceGenerationJobRetentionScheduler` purges completed jobs older than 30 days

---

## 3) Step-by-Step Implementation Details

### 3.1 Invoice request initiation
Client sends `POST /tenants/{tenantId}/invoices:generate` with `GenerateInvoiceRequest`.

### 3.2 Request validation and persistence

- Request DTO validation happens first (`@Valid` in controller + Jakarta validations in request DTO).
- Service builds natural job key:

```text
inv-job::{tenantId}::{unitId}::{YYYYMM}
```

- If job already exists (same tenant + unit + month/year), existing job is returned (dedupe).
- Otherwise, a new job document is created with:
  - `status = SUBMITTED`
  - tenant/unit/month/year metadata
  - timestamps (`createdAt`, `updatedAt`)

### 3.3 Async trigger (background worker)

- `CompletableFuture.supplyAsync(...)` starts background execution.
- Dedicated executor bean (`invoiceGenerationExecutor`) is used.
- Inside async flow, `FutureTask<Invoice>` wraps `invoiceService.generate(...)` and executes in worker thread.

### 3.4 Invoice processing logic

- Existing `InvoiceService.generate(...)` is reused unchanged for core computation.
- Processing uses same business rules for:
  - opening/closing balances,
  - profile charges,
  - issue/due date resolution,
  - persistence and model validation.

### 3.5 Success and failure handling

- On start: job -> `RUNNING`, `startedAt` set.
- On success: job -> `SUCCEEDED`, `invoiceId` set, `completedAt` set.
- On failure: job -> `FAILED`, `errorMessage` set, `completedAt` set.
- Root exception message is captured for traceable status output.

### 3.6 Status retrieval

- Client calls `GET /tenants/{tenantId}/invoices/jobs/{jobId}`.
- Response contains current status + metadata.
- Tenant guard in service ensures cross-tenant job access is blocked.

### 3.7 Retention and cleanup

- Completed jobs are retained for 30 days.
- Scheduler runs daily (`cleanup-cron`) and deletes only jobs where:
  - status in (`SUCCEEDED`, `FAILED`), and
  - `completedAt < now - retentionDays`.
- In-progress jobs (`SUBMITTED`, `RUNNING`) are never purged by this cleanup.

### Threading / queue mechanism used

- **Used:** in-process async execution via `ThreadPoolTaskExecutor`.
- **Not used currently:** external queue broker (Kafka/RabbitMQ/SQS).

### Error handling and retry strategy

- Current strategy: capture failure in job state (`FAILED`) with `errorMessage`.
- No automatic retry loop implemented in current version.
- Retry is currently client-driven by resubmitting after failure (or operational intervention).

### Idempotency and consistency handling

- **Natural-key dedupe** prevents duplicate job creation for same tenant+unit+period.
- Job persistence ensures lifecycle is observable and auditable.
- Invoice generation itself remains naturally idempotent by invoice ID pattern in `InvoiceService`.

---

## 4) Side-by-Side Code Comparison

### Before (Synchronous approach)

**Behavior:** Request blocks until invoice generation completes.

```java
@PostMapping(":generate")
public ResponseEntity<Invoice> generate(@PathVariable String tenantId,
                                        @Valid @RequestBody GenerateInvoiceRequest req) {
    return ResponseEntity.status(201).body(invoiceService.generate(tenantId, req));
}
```

### After (Asynchronous approach)

**Behavior:** Request returns immediately with job info; processing continues in background.

```java
@PostMapping(":generate")
public ResponseEntity<InvoiceGenerationJobResponse> generate(@PathVariable String tenantId,
                                                             @Valid @RequestBody GenerateInvoiceRequest req) {
    InvoiceGenerationJobResponse response = asyncInvoiceGenerationService.submit(tenantId, req);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
}

@GetMapping("/jobs/{jobId}")
public ResponseEntity<InvoiceGenerationJobResponse> getJobStatus(@PathVariable String tenantId,
                                                                 @PathVariable String jobId) {
    return ResponseEntity.ok(asyncInvoiceGenerationService.getStatus(tenantId, jobId));
}
```

### Core differences

| Area | Before (Sync) | After (Async) |
|---|---|---|
| Control flow | Linear, blocking in API thread | Split: submit + background worker + status polling |
| API response time | Depends on full processing | Fast acknowledgment (`202`) |
| Resource utilization | Ties up request threads longer | Frees request threads quickly; uses worker pool |
| UX | User waits for final result | User gets job ID and trackable progress |
| Observability | Limited to request lifecycle | Explicit job states persisted in DB |

---

## 5) Testing Workflow for the New Approach

### Test layers

- **Unit tests**
  - `AsyncInvoiceGenerationServiceTest`
  - Validates dedupe and successful async transitions.
- **Scheduler tests**
  - `InvoiceGenerationJobRetentionSchedulerTest`
  - Validates 30-day retention cleanup behavior.
- **Existing domain behavior tests**
  - `InvoiceServiceDateComputationTest`
  - Confirms invoice date logic remains stable.

### End-to-end test workflow (recommended)

1. Submit invoice generation request (`POST :generate`).
2. Verify response is `202` with `jobId`.
3. Poll status endpoint (`GET /jobs/{jobId}`).
4. Validate state transitions:
   - `SUBMITTED` -> `RUNNING` -> `SUCCEEDED` (or `FAILED`).
5. On success, confirm `invoiceId` is present and invoice exists.
6. On failure, verify `errorMessage` is available in job response.
7. Verify dedupe by submitting same tenant+unit+month/year again; same job should be returned.
8. Retention validation:
   - Seed old completed jobs,
   - trigger/await scheduled cleanup,
   - confirm jobs older than 30 days are removed.

### Monitoring and logs

- Monitor logs from async service and retention scheduler for lifecycle transitions.
- Ensure error logs are correlated by `jobId` for debugging.
- Validate cleanup logs include number of purged jobs.

---

## 6) User Use Case and Experience

### What users experience now

1. User triggers invoice generation.
2. System immediately confirms request and provides `jobId`.
3. UI/backend polls job status.
4. Once complete, user sees generated invoice (or actionable failure message).

### User-visible improvements vs old flow

- Faster response after clicking generate.
- Better transparency (clear processing status).
- Fewer timeout-like waiting scenarios.
- Better reliability under heavier traffic.

---

## API Contract Summary (Current)

### Submit generation
`POST /tenants/{tenantId}/invoices:generate`

**Response:** `202 Accepted`

```json
{
  "jobId": "inv-job::tenant::1::unit::101::202604",
  "status": "SUBMITTED",
  "invoiceId": null,
  "errorMessage": null
}
```

### Check status
`GET /tenants/{tenantId}/invoices/jobs/{jobId}`

**Response (example success):**

```json
{
  "jobId": "inv-job::tenant::1::unit::101::202604",
  "status": "SUCCEEDED",
  "invoiceId": "INV-unit::101-202604",
  "errorMessage": null,
  "createdAt": "...",
  "updatedAt": "...",
  "startedAt": "...",
  "completedAt": "..."
}
```

---

## Operational Notes

- Ensure Couchbase collection exists:
  - bucket: `prop-tax`
  - scope: `main`
  - collection: `invoice_generation_jobs`
- Config knobs are in `application.yml`:
  - worker pool sizing
  - retention days (30)
  - cleanup schedule cron

---

## Current Limitations / Future Enhancements

- No automatic retry policy for failed jobs (currently manual/client retry).
- In-process async execution (single service boundary); can be extended to broker-based queue for distributed worker scaling.
- Status push notifications/webhooks are not yet implemented (polling-based status retrieval today).

