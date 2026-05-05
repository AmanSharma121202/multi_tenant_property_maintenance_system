# Kafka Flow Diagrams - Billing App

This file shows the active Kafka flows in the current billing application.

---

## 1) Tenant invoice due flow

```text
[InvoiceGenerationScheduler]
        |
        | 1. cron runs every minute
        | 2. loads tenants from Couchbase
        | 3. checks invoice anchor date (If Today)
        | 4. checks invoice does not already exist
        v
[InvoiceFlowEventPublisher.publishTenantInvoiceDue]
        |
        | publishes TenantInvoiceDueEvent
        | key = tenantId
        v
[Kafka topic: billing.tenant.invoice.due.v1]
        |
        | partition 0 in local single-node setup
        | offset increases for each new message
        v
[InvoiceFlowEventsListener.onTenantInvoiceDue]
        |
        | 1. reads ConsumerRecord
        | 2. converts record.value() to TenantInvoiceDueEvent
        | 3. logs topic / partition / offset
        | 4. schedules async invoice generation
        v
[AsyncInvoiceGenerationService.generateForTenantUnits]
        |
        | 1. loads all units for the tenant
        | 2. loops unit by unit
        | 3. creates GenerateInvoiceRequest
        | 4. calls InvoiceService.generate()
        v
[InvoiceService.generate]
        |
        | 1. builds invoice id: INV-<unit>-<year><month>
        | 2. checks if invoice already exists
        | 3. loads unit + profile
        | 4. computes balances and dates
        | 5. saves invoice to Couchbase
        v
[Couchbase invoices collection]
```

### What this flow means
- The scheduler does **not** create invoices directly.
- Kafka is only the handoff mechanism.
- The listener and async service do the actual work.
- The invoice is stored only after `InvoiceService.generate()` saves it.

---

## 2) Owner-unit linked backfill flow

```text
[UnitService or OwnerService]
        |
        | 1. owner is linked to a unit
        | 2. event is created
        v
[InvoiceFlowEventPublisher.publishOwnerUnitLinked]
        |
        | publishes OwnerUnitLinkedEvent
        | key = unitId
        v
[Kafka topic: billing.owner.unit.linked.v1]
        |
        v
[InvoiceFlowEventsListener.onOwnerUnitLinked]
        |
        | 1. reads ConsumerRecord
        | 2. converts record.value() to OwnerUnitLinkedEvent
        | 3. calls InvoiceService.backfillOwnerForUnitInvoices()
        v
[InvoiceService.backfillOwnerForUnitInvoices]
        |
        | 1. loads invoices for the unit
        | 2. finds invoices with missing ownerId
        | 3. updates ownerId
        | 4. saves back to Couchbase
        v
[Couchbase invoices collection]
```

### What this flow means
- This flow is used to repair or complete invoice data.
- Existing invoices can be updated when an owner is linked later.
- Kafka makes the owner-link change visible to invoice processing asynchronously.

---

## 3) Event lifecycle with Kafka metadata

```text
Producer sends event
    |
    | key = tenantId or unitId
    v
Kafka stores event in a partition
    |
    | offset is assigned
    v
Consumer reads event
    |
    | topic / partition / offset are logged
    v
Listener dispatches to service
    |
    v
Database is updated
```

### Important metadata used in logs
- **topic**: which Kafka topic carried the message
- **partition**: which partition stored the message
- **offset**: where the message sits in that partition
- **key**: determines routing and ordering

Example log style:
```text
Consumed tenant invoice due event: tenantId=tenant::1 topic=billing.tenant.invoice.due.v1 partition=0 offset=25
```

---

## 4) Error path for bad Kafka messages

```text
Kafka message arrives
        |
        v
ErrorHandlingDeserializer / JsonDeserializer
        |
        | if payload is malformed
        v
DefaultErrorHandler in KafkaConsumerConfig
        |
        | retries a few times
        | then publishes to .DLQ topic
        v
Dead letter topic: billing.tenant.invoice.due.v1.DLQ
```

### What this protects
- A bad message does not stop the consumer forever.
- Good messages can continue to be processed.
- Broken payloads are isolated for later inspection.

---

## 5) One-line summary diagrams

### Tenant invoice due
```text
Scheduler → Producer → Kafka Topic → Listener (Consumer)→ Async Service → Invoice Service → Couchbase
```

### Owner backfill
```text
Owner / Unit Change Event → Producer → Kafka Topic → Listener (Consumer) → Invoice Service → Couchbase
```

---

