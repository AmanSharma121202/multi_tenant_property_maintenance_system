package com.housing.billing.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceFlowEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.tenant-invoice-due}")
    private String tenantInvoiceDueTopic;

    @Value("${app.kafka.topics.owner-unit-linked}")
    private String ownerUnitLinkedTopic;

    public void publishTenantInvoiceDue(TenantInvoiceDueEvent event) {
        log.info("Publishing tenant invoice due event: eventId={} tenantId={} billingDate={} topic={} key={}",
                event.getEventId(), event.getTenantId(), event.getBillingDate(), tenantInvoiceDueTopic, event.getTenantId());

        kafkaTemplate.send(tenantInvoiceDueTopic, event.getTenantId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish tenant invoice due event: eventId={} tenantId={} billingDate={} topic={} reason={}",
                                event.getEventId(), event.getTenantId(), event.getBillingDate(), tenantInvoiceDueTopic, ex.getMessage());
                    } else {
                        log.info("Published tenant invoice due event: eventId={} tenantId={} billingDate={} topic={} partition={} offset={}",
                                event.getEventId(), event.getTenantId(), event.getBillingDate(),
                                result.getRecordMetadata().topic(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }

    public void publishOwnerUnitLinked(OwnerUnitLinkedEvent event) {
        kafkaTemplate.send(ownerUnitLinkedTopic, event.getUnitId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish owner-unit linked event: tenantId={}, unitId={}, ownerId={}, reason={}",
                                event.getTenantId(), event.getUnitId(), event.getOwnerId(), ex.getMessage());
                    } else {
                        log.info("Published owner-unit linked event: tenantId={}, unitId={}, ownerId={}",
                                event.getTenantId(), event.getUnitId(), event.getOwnerId());
                    }
                });
    }
}

