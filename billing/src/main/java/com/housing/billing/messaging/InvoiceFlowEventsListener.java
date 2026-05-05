package com.housing.billing.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housing.billing.repository.InvoiceRepository;
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
    private final InvoiceRepository invoiceRepository;
    private final ObjectMapper objectMapper;

    public InvoiceFlowEventsListener(AsyncInvoiceGenerationService asyncInvoiceGenerationService,
                                     InvoiceService invoiceService,
                                     InvoiceRepository invoiceRepository,
                                     ObjectMapper objectMapper) {
        this.asyncInvoiceGenerationService = asyncInvoiceGenerationService;
        this.invoiceService = invoiceService;
        this.invoiceRepository = invoiceRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.tenant-invoice-due}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onTenantInvoiceDue(ConsumerRecord<String, Object> record) {
        Object payload = record.value();
        log.info("Received tenant invoice due event: topic={} partition={} offset={} key={} payloadType={}",
                record.topic(), record.partition(), record.offset(), record.key(),
                payload == null ? "null" : payload.getClass().getName());

        TenantInvoiceDueEvent event;
        try {
            event = toEvent(payload, TenantInvoiceDueEvent.class);
        } catch (Exception ex) {
            log.error("Failed to parse tenant invoice due event: topic={} partition={} offset={} key={} payloadType={} reason={}",
                    record.topic(), record.partition(), record.offset(), record.key(),
                    payload == null ? "null" : payload.getClass().getName(), ex.getMessage());
            throw ex;
        }

        Duration delay = Duration.ofSeconds(Math.max(0L, event.getDelaySeconds()));
        if (!invoiceRepository.findAnyByTenantIdAndYearAndMonth(
                event.getTenantId(),
                event.getBillingDate().getYear(),
                event.getBillingDate().getMonthValue()
        ).isEmpty()) {
            log.info("Skipping tenant invoice generation; already exists: eventId={} tenantId={} cycle={}-{} topic={} partition={} offset={}",
                    event.getEventId(), event.getTenantId(), event.getBillingDate().getYear(), event.getBillingDate().getMonthValue(),
                    record.topic(), record.partition(), record.offset());
            return;
        }

        log.info("Dispatching tenant invoice event for async generation: eventId={} tenantId={} billingDate={} delaySeconds={} topic={} partition={} offset={}",
                event.getEventId(), event.getTenantId(), event.getBillingDate(), event.getDelaySeconds(),
                record.topic(), record.partition(), record.offset());

        asyncInvoiceGenerationService.scheduleTenantInvoiceGeneration(
                event.getTenantId(),
                event.getBillingDate(),
                delay,
                event.getEventId()
        );
        log.info("Consumed tenant invoice due event: eventId={} tenantId={} billingDate={} delaySeconds={} topic={} partition={} offset={}",
                event.getEventId(), event.getTenantId(), event.getBillingDate(), event.getDelaySeconds(),
                record.topic(), record.partition(), record.offset());
    }

    @KafkaListener(
            topics = "${app.kafka.topics.owner-unit-linked}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onOwnerUnitLinked(ConsumerRecord<String, Object> record) {
        Object payload = record.value();
        log.info("Received owner-unit linked event: topic={} partition={} offset={} key={} payloadType={}",
                record.topic(), record.partition(), record.offset(), record.key(),
                payload == null ? "null" : payload.getClass().getName());

        OwnerUnitLinkedEvent event;
        try {
            event = toEvent(payload, OwnerUnitLinkedEvent.class);
        } catch (Exception ex) {
            log.error("Failed to parse owner-unit linked event: topic={} partition={} offset={} key={} payloadType={} reason={}",
                    record.topic(), record.partition(), record.offset(), record.key(),
                    payload == null ? "null" : payload.getClass().getName(), ex.getMessage());
            throw ex;
        }

        invoiceService.backfillOwnerForUnitInvoices(event.getTenantId(), event.getUnitId(), event.getOwnerId());
        log.info("Consumed owner-unit linked event: eventId={} tenantId={} unitId={} ownerId={} topic={} partition={} offset={}",
                event.getEventId(), event.getTenantId(), event.getUnitId(), event.getOwnerId(),
                record.topic(), record.partition(), record.offset());
    }

    private <T> T toEvent(Object payload, Class<T> type) {
        if (type.isInstance(payload)) {
            return type.cast(payload);
        }
        return objectMapper.convertValue(payload, type);
    }

}
