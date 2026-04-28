package com.housing.billing.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housing.billing.service.AsyncInvoiceGenerationService;
import com.housing.billing.service.InvoiceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class InvoiceFlowEventsListener {

    private final AsyncInvoiceGenerationService asyncInvoiceGenerationService;
    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;

    public InvoiceFlowEventsListener(AsyncInvoiceGenerationService asyncInvoiceGenerationService,
                                     InvoiceService invoiceService,
                                     ObjectMapper objectMapper) {
        this.asyncInvoiceGenerationService = asyncInvoiceGenerationService;
        this.invoiceService = invoiceService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.tenant-invoice-due}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onTenantInvoiceDue(ConsumerRecord<String, Object> record) {
        TenantInvoiceDueEvent event;
        try {
            event = toEvent(record.value(), TenantInvoiceDueEvent.class);
        } catch (Exception ex) {
            Object payload = record.value();
            log.error("Failed to parse tenant invoice due event: topic={} partition={} offset={} key={} payloadType={} reason={}",
                    record.topic(), record.partition(), record.offset(), record.key(),
                    payload == null ? "null" : payload.getClass().getName(), ex.getMessage());
            throw ex;
        }

        Duration delay = Duration.ofSeconds(Math.max(0L, event.getDelaySeconds()));
        log.info("Dispatching tenant invoice event for async generation: eventId={} tenantId={} billingDate={} delaySeconds={} topic={} partition={} offset={}",
                event.getEventId(), event.getTenantId(), event.getBillingDate(), event.getDelaySeconds(),
                record.topic(), record.partition(), record.offset());

        asyncInvoiceGenerationService.scheduleTenantInvoiceGeneration(
                event.getTenantId(),
                event.getBillingDate(),
                delay,
                event.getEventId()
        );
        log.info("Consumed tenant invoice due event: tenantId={}, billingDate={}, delaySeconds={}, topic={}, partition={}, offset={}",
                event.getTenantId(), event.getBillingDate(), event.getDelaySeconds(),
                record.topic(), record.partition(), record.offset());
    }

    @KafkaListener(
            topics = "${app.kafka.topics.owner-unit-linked}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onOwnerUnitLinked(ConsumerRecord<String, Object> record) {
        OwnerUnitLinkedEvent event = toEvent(record.value(), OwnerUnitLinkedEvent.class);
        invoiceService.backfillOwnerForUnitInvoices(event.getTenantId(), event.getUnitId(), event.getOwnerId());
        log.info("Consumed owner-unit linked event: tenantId={}, unitId={}, ownerId={}, topic={}, partition={}, offset={}",
                event.getTenantId(), event.getUnitId(), event.getOwnerId(),
                record.topic(), record.partition(), record.offset());
    }

    private <T> T toEvent(Object payload, Class<T> type) {
        if (type.isInstance(payload)) {
            return type.cast(payload);
        }
        return objectMapper.convertValue(payload, type);
    }

}
