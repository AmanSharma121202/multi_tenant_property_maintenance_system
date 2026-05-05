# Kafka Concepts in the Billing Application

This guide explains the Kafka concepts used in the current billing app, with references to the active code paths and topics.

## What Kafka is doing here
Kafka is used as a message broker to transfer tasks from the scheduler to the invoice processor asynchronously.

## 1) Topic
A topic is a named channel in Kafka.

### Active topics in this app
- `billing.tenant.invoice.due.v1`
- `billing.owner.unit.linked.v1`

### Where configured
`src/main/resources/application.yml`
```yaml
app:
  kafka:
    topics:
      tenant-invoice-due: billing.tenant.invoice.due.v1
      owner-unit-linked: billing.owner.unit.linked.v1
```

### Where used
- `InvoiceGenerationScheduler.scheduleTenantInvoices()` creates an event and calls `publishTenantInvoiceDue()`
- `InvoiceFlowEventPublisher.publishTenantInvoiceDue()` sends the event to `tenant-invoice-due` topic
- `InvoiceFlowEventsListener.onTenantInvoiceDue()` consumes the event from `tenant-invoice-due` topic
- `UnitService.triggerOwnerBackfill` or `OwnerService.triggerOwnerBackfill` creates an event and calls `publishOwnerUnitLinked()`
- `InvoiceFlowEventPublisher.publishOwnerUnitLinked()` sends to the event to `owner-unit-linked` topic
- `InvoiceFlowEventsListener.onOwnerUnitLinked()` consumes the event from`owner-unit-linked` topic

---

## 2) Producer
A producer sends a message to Kafka.

### Producer class
`src/main/java/com/housing/billing/messaging/InvoiceFlowEventPublisher.java`

### Methods
- `publishTenantInvoiceDue(TenantInvoiceDueEvent event)`
- `publishOwnerUnitLinked(OwnerUnitLinkedEvent event)`

### How it works
The publisher uses `KafkaTemplate<String, Object>`:
```java
kafkaTemplate.send(tenantInvoiceDueTopic, event.getTenantId(), event)
```
The `tenantId` is the key, which helps keep the same tenant’s events on the same partition.

### Important behavior
- Sends asynchronously
- Logs success/failure
- Does not block the scheduler thread

---

## 3) Consumer
A consumer reads Kafka messages and processes them.

### Consumer class
`src/main/java/com/housing/billing/messaging/InvoiceFlowEventsListener.java`

### Methods
- `onTenantInvoiceDue(ConsumerRecord<String, Object> record)`
- `onOwnerUnitLinked(ConsumerRecord<String, Object> record)`

### What happens
- Kafka delivers a `ConsumerRecord`
- The listener converts `record.value()` into the event class
- For tenant due events, it schedules async invoice generation
- For owner-unit-linked events, it backfills owner IDs on invoices

### Important detail
The listener uses `record.value()`, not the full `ConsumerRecord`, for conversion.

---

## 4) Topic key and partitioning
A key determines which partition receives the message.

### In this app
- Key for invoice-due events: `tenantId`
- Key for owner-unit-linked events: `unitId`

### Why it matters
Using the tenant ID keeps all messages for the same tenant ordered.
That means invoice generation for a tenant is processed consistently.

---

## 5) Partition
A partition is an ordered slice of a topic.

### In this app
The Kafka setup is single-node, so topics are effectively handled in a simple local setup.

### Why it matters
- Messages in a partition stay in order
- One consumer in the group handles each partition
- More consumers can be added later for scaling

---

## 6) Consumer group
A consumer group is a set of consumers that share work.

### Group used here
`billing-service`

### Where configured
`src/main/resources/application.yml`
```yaml
spring:
  kafka:
    consumer:
      group-id: billing-service
```

### Why it matters
- If the app runs multiple times, Kafka can balance partitions between them
- Each message is processed by one consumer in the group

---

## 7) Offset
An offset is the message position inside a partition.

### Why it matters
Kafka uses offsets to know what was already consumed.
If the app restarts, it continues from the last committed offset.

### Where you see it
Listener logs include topic, partition, and offset.

Example:
```text
Consumed tenant invoice due event: ... topic=billing.tenant.invoice.due.v1 partition=0 offset=25
```

---

## 8) Serialization and deserialization
Kafka sends bytes, not Java objects.

### Producer side
`JsonSerializer` converts Java objects to JSON bytes.

### Consumer side
`ErrorHandlingDeserializer` + `JsonDeserializer` converts JSON bytes back to objects.

### Where configured
`src/main/resources/application.yml`
```yaml
spring:
  kafka:
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.value.default.type: java.util.LinkedHashMap
        spring.json.trusted.packages: "com.housing.billing.*"
```

### Why `LinkedHashMap` is used
It gives the listener a generic JSON object first, and then `ObjectMapper.convertValue(...)` converts it into the target event class.

---

## 9) Event DTOs
These are the message payload classes.

### `TenantInvoiceDueEvent`
File: `src/main/java/com/housing/billing/messaging/TenantInvoiceDueEvent.java`

Fields:
- `eventId`
- `tenantId`
- `billingDate`
- `delaySeconds`
- `occurredAt`

### `OwnerUnitLinkedEvent`
File: `src/main/java/com/housing/billing/messaging/OwnerUnitLinkedEvent.java`

Fields typically include:
- `eventId`
- `tenantId`
- `unitId`
- `ownerId`

---

## 10) Async processing
Kafka only carries the trigger. Actual invoice generation happens asynchronously.

### Service
`src/main/java/com/housing/billing/service/AsyncInvoiceGenerationService.java`

### Key methods
- `scheduleTenantInvoiceGeneration(...)`
- `generateForTenantUnits(...)`

### What it does
- Loads all units for the tenant
- Loops through each unit
- Calls `InvoiceService.generate(...)` for each one
- Logs success/failure per unit

### Why it matters
If one unit fails, other units still continue.

---

## 11) Invoice generation
This is the real business logic.

### Service
`src/main/java/com/housing/billing/service/InvoiceService.java`

### Key method
- `generate(String tenantId, GenerateInvoiceRequest req)`

### What it does
- Builds a natural invoice ID like `INV-unit::1-202604`
- Checks if invoice already exists
- Loads the unit and profile
- Calculates balances and due dates
- Saves invoice to Couchbase

---

## 12) Idempotency
Idempotency means the same message can be handled more than once without creating duplicates.

### In this app
There are two protections:
1. Scheduler checks if invoices already exist before publishing.
2. InvoiceService uses a deterministic invoice ID and checks Couchbase first.

### Result
Duplicate events should not create duplicate invoices.

---

## 13) Error handling
Kafka and the listener are configured to handle bad messages safely.

### Kafka config
`src/main/java/com/housing/billing/config/KafkaConsumerConfig.java`

### Important behavior
- `DefaultErrorHandler` retries a failed record a few times
- Records that cannot be deserialized are treated as not retryable
- They are routed to a `.DLQ` topic by the `DeadLetterPublishingRecoverer`

### What this means
If a bad Kafka message arrives, the app can move it out of the main stream instead of crashing the consumer loop.

---

## 14) Late fee scheduler
This is not Kafka, but it uses the invoices produced by the Kafka flow.

### Flow
- Invoice is created and stored
- Later, the late fee scheduler scans overdue invoices
- It updates the invoice by calling `InvoiceService.recalculate(...)`

---

## Quick reference table

| Kafka concept | Where it appears | Why it matters |
|---|---|---|
| Topic | `application.yml` | Message channel |
| Producer | `InvoiceFlowEventPublisher` | Sends events |
| Consumer | `InvoiceFlowEventsListener` | Reads events |
| Partition key | `tenantId` / `unitId` | Ordering and routing |
| Offset | Listener logs | Position tracking |
| Consumer group | `billing-service` | Shared consumption |
| Serialization | `JsonSerializer` | Java object to bytes |
| Deserialization | `JsonDeserializer` | Bytes to Java object |
| Idempotency | Scheduler + InvoiceService | Prevent duplicates |
| DLQ | `KafkaConsumerConfig` | Safe handling of bad messages |

---

## Bottom line
Kafka in this app is used for two active event flows:
- **Tenant invoice due** → triggers invoice generation
- **Owner-unit linked** → backfills old invoices with owner IDs

Everything else in the invoice pipeline is regular service and database logic after Kafka delivers the event.
