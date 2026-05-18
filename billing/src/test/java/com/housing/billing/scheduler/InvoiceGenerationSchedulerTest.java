package com.housing.billing.scheduler;

import com.housing.billing.messaging.InvoiceFlowEventPublisher;
import com.housing.billing.model.Tenant;
import com.housing.billing.repository.TenantRepository;
import com.housing.billing.service.AsyncInvoiceGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceGenerationSchedulerTest {

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private InvoiceFlowEventPublisher invoiceFlowEventPublisher;

	@Mock
	private AsyncInvoiceGenerationService asyncInvoiceGenerationService;

	@InjectMocks
	private InvoiceGenerationScheduler scheduler;

	@Test
	void scheduleTenantInvoices_publishesOnlyTenantsMatchingToday() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");
		ReflectionTestUtils.setField(scheduler, "kafkaEnabled", true);

		LocalDate today = LocalDate.now();
		Tenant dueTenant = tenant("tenant::1", today);
		Tenant futureTenant = tenant("tenant::2", today.plusDays(1));

		when(tenantRepository.findAllTenants()).thenReturn(List.of(dueTenant, futureTenant));

		scheduler.scheduleTenantInvoices();

		verify(invoiceFlowEventPublisher, times(1)).publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void scheduleTenantInvoices_publishesTenantOnEachTickWhenDueToday() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");
		ReflectionTestUtils.setField(scheduler, "kafkaEnabled", true);

		LocalDate today = LocalDate.now();
		Tenant dueTenant = tenant("tenant::1", today);
		when(tenantRepository.findAllTenants()).thenReturn(List.of(dueTenant));

		scheduler.scheduleTenantInvoices();
		scheduler.scheduleTenantInvoices();

		verify(invoiceFlowEventPublisher, times(2)).publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void scheduleTenantInvoices_whenKafkaEnabled_publishesDueEvent() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");
		ReflectionTestUtils.setField(scheduler, "kafkaEnabled", true);

		LocalDate today = LocalDate.now();
		when(tenantRepository.findAllTenants()).thenReturn(List.of(tenant("tenant::1", today)));

		scheduler.scheduleTenantInvoices();

		verify(invoiceFlowEventPublisher, times(1)).publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void scheduleTenantInvoices_matchesByDayOfMonthAndPublishesForCurrentDate() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");
		ReflectionTestUtils.setField(scheduler, "kafkaEnabled", true);

		LocalDate today = LocalDate.now();
		Tenant monthlyTenant = tenant("tenant::1", LocalDate.of(2020, 1, today.getDayOfMonth()));
		when(tenantRepository.findAllTenants()).thenReturn(List.of(monthlyTenant));

		scheduler.scheduleTenantInvoices();

		verify(invoiceFlowEventPublisher, times(1)).publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void scheduleTenantInvoices_whenKafkaDisabled_dispatchesInProcess() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");
		ReflectionTestUtils.setField(scheduler, "kafkaEnabled", false);

		LocalDate today = LocalDate.now();
		when(tenantRepository.findAllTenants()).thenReturn(List.of(tenant("tenant::1", today)));

		scheduler.scheduleTenantInvoices();

		verify(asyncInvoiceGenerationService, times(1))
				.scheduleTenantInvoiceGeneration(org.mockito.ArgumentMatchers.anyString(),
						org.mockito.ArgumentMatchers.any(LocalDate.class),
						org.mockito.ArgumentMatchers.eq(Duration.ZERO),
						org.mockito.ArgumentMatchers.anyString());
		verify(invoiceFlowEventPublisher, never()).publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void scheduleTenantInvoices_whenPastDue_publishesDueEvent() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");
		ReflectionTestUtils.setField(scheduler, "kafkaEnabled", true);

		LocalDate today = LocalDate.now();
		Tenant pastDueTenant = tenant("tenant::1", today.minusDays(2));
		when(tenantRepository.findAllTenants()).thenReturn(List.of(pastDueTenant));

		scheduler.scheduleTenantInvoices();

		verify(invoiceFlowEventPublisher, times(1)).publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void scheduleTenantInvoices_whenTenantQueryFails_doesNotThrowAndSkipsPublishing() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");
		ReflectionTestUtils.setField(scheduler, "kafkaEnabled", true);

		when(tenantRepository.findAllTenants()).thenThrow(new RuntimeException("db down"));

		assertDoesNotThrow(() -> scheduler.scheduleTenantInvoices());

		verify(invoiceFlowEventPublisher, never()).publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void scheduleTenantInvoices_whenSingleTenantPublishFails_continuesWithOthers() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");
		ReflectionTestUtils.setField(scheduler, "kafkaEnabled", true);

		LocalDate today = LocalDate.now();
		Tenant failingTenant = tenant("tenant::1", today);
		Tenant healthyTenant = tenant("tenant::2", today);
		when(tenantRepository.findAllTenants()).thenReturn(List.of(failingTenant, healthyTenant));
		doThrow(new RuntimeException("publish failed"))
				.doNothing()
				.when(invoiceFlowEventPublisher)
				.publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());

		assertDoesNotThrow(() -> scheduler.scheduleTenantInvoices());

		verify(invoiceFlowEventPublisher, times(2)).publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());
	}

	private Tenant tenant(String id, LocalDate invoiceDate) {
		Tenant tenant = new Tenant();
		tenant.setId(id);
		tenant.setBillingDate(invoiceDate);
		return tenant;
	}
}
