package com.housing.billing.scheduler;

import com.housing.billing.messaging.InvoiceFlowEventPublisher;
import com.housing.billing.model.Tenant;
import com.housing.billing.repository.InvoiceRepository;
import com.housing.billing.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class InvoiceGenerationSchedulerTest {

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private InvoiceRepository invoiceRepository;

	@Mock
	private InvoiceFlowEventPublisher invoiceFlowEventPublisher;

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
		when(invoiceRepository.findAnyByTenantIdAndYearAndMonth(anyString(), anyInt(), anyInt())).thenReturn(List.of());

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
		when(invoiceRepository.findAnyByTenantIdAndYearAndMonth(anyString(), anyInt(), anyInt())).thenReturn(List.of());

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
		when(invoiceRepository.findAnyByTenantIdAndYearAndMonth(anyString(), anyInt(), anyInt())).thenReturn(List.of());

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
		when(invoiceRepository.findAnyByTenantIdAndYearAndMonth(anyString(), anyInt(), anyInt())).thenReturn(List.of());

		scheduler.scheduleTenantInvoices();

		verify(invoiceFlowEventPublisher, times(1)).publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void scheduleTenantInvoices_whenKafkaDisabled_skipsPublishing() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");
		ReflectionTestUtils.setField(scheduler, "kafkaEnabled", false);

		scheduler.scheduleTenantInvoices();

		verify(invoiceFlowEventPublisher, never()).publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());
		verify(tenantRepository, never()).findAllTenants();
	}

	@Test
	void scheduleTenantInvoices_whenPastDueAndCurrentCycleMissing_publishesDueEvent() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");
		ReflectionTestUtils.setField(scheduler, "kafkaEnabled", true);

		LocalDate today = LocalDate.now();
		Tenant pastDueTenant = tenant("tenant::1", today.minusDays(2));
		when(tenantRepository.findAllTenants()).thenReturn(List.of(pastDueTenant));
		when(invoiceRepository.findAnyByTenantIdAndYearAndMonth(anyString(), anyInt(), anyInt())).thenReturn(List.of());

		scheduler.scheduleTenantInvoices();

		verify(invoiceFlowEventPublisher, times(1)).publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void scheduleTenantInvoices_whenCurrentCycleAlreadyGenerated_skipsPublishing() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");
		ReflectionTestUtils.setField(scheduler, "kafkaEnabled", true);

		LocalDate today = LocalDate.now();
		Tenant dueTenant = tenant("tenant::1", today);
		when(tenantRepository.findAllTenants()).thenReturn(List.of(dueTenant));
		when(invoiceRepository.findAnyByTenantIdAndYearAndMonth(anyString(), anyInt(), anyInt()))
				.thenReturn(List.of(new com.housing.billing.model.Invoice()));

		scheduler.scheduleTenantInvoices();

		verify(invoiceFlowEventPublisher, never()).publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());
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
	void scheduleTenantInvoices_whenSingleTenantCheckFails_continuesWithOthers() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");
		ReflectionTestUtils.setField(scheduler, "kafkaEnabled", true);

		LocalDate today = LocalDate.now();
		Tenant failingTenant = tenant("tenant::1", today);
		Tenant healthyTenant = tenant("tenant::2", today);
		when(tenantRepository.findAllTenants()).thenReturn(List.of(failingTenant, healthyTenant));
		when(invoiceRepository.findAnyByTenantIdAndYearAndMonth("tenant::1", today.getYear(), today.getMonthValue()))
				.thenThrow(new RuntimeException("query timeout"));
		when(invoiceRepository.findAnyByTenantIdAndYearAndMonth("tenant::2", today.getYear(), today.getMonthValue()))
				.thenReturn(List.of());

		assertDoesNotThrow(() -> scheduler.scheduleTenantInvoices());

		verify(invoiceFlowEventPublisher, times(1)).publishTenantInvoiceDue(org.mockito.ArgumentMatchers.any());
	}

	private Tenant tenant(String id, LocalDate invoiceDate) {
		Tenant tenant = new Tenant();
		tenant.setId(id);
		tenant.setInvoiceDate(invoiceDate);
		return tenant;
	}
}

