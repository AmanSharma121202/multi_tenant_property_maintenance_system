package com.housing.billing.scheduler;

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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceGenerationSchedulerTest {

	@Mock
	private TenantRepository tenantRepository;

	@Mock
	private AsyncInvoiceGenerationService asyncInvoiceGenerationService;

	@InjectMocks
	private InvoiceGenerationScheduler scheduler;

	@Test
	void scheduleTenantInvoices_schedulesOnlyTenantsMatchingToday() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");

		LocalDate today = LocalDate.now();
		Tenant dueTenant = tenant("tenant::1", today);
		Tenant futureTenant = tenant("tenant::2", today.plusDays(1));

		when(tenantRepository.findAllTenants()).thenReturn(List.of(dueTenant, futureTenant));

		scheduler.scheduleTenantInvoices();

		verify(asyncInvoiceGenerationService).scheduleTenantInvoiceGeneration(
				"tenant::1",
				today,
				Duration.ofMinutes(1)
		);
		verify(asyncInvoiceGenerationService, never()).scheduleTenantInvoiceGeneration(
				"tenant::2",
				futureTenant.getInvoiceDate(),
				Duration.ofMinutes(1)
		);
	}

	@Test
	void scheduleTenantInvoices_schedulesTenantOnEachTickWhenDueToday() {
		ReflectionTestUtils.setField(scheduler, "tenantTimezone", "UTC");

		LocalDate today = LocalDate.now();
		Tenant dueTenant = tenant("tenant::1", today);
		when(tenantRepository.findAllTenants()).thenReturn(List.of(dueTenant));

		scheduler.scheduleTenantInvoices();
		scheduler.scheduleTenantInvoices();

		verify(asyncInvoiceGenerationService, times(2)).scheduleTenantInvoiceGeneration(
				"tenant::1",
				today,
				Duration.ofMinutes(1)
		);
	}

	private Tenant tenant(String id, LocalDate invoiceDate) {
		Tenant tenant = new Tenant();
		tenant.setId(id);
		tenant.setInvoiceDate(invoiceDate);
		return tenant;
	}
}

