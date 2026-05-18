package com.housing.billing.repository;

import com.housing.billing.model.Invoice;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.time.Instant;

@Repository
public interface InvoiceRepository extends CouchbaseRepository<Invoice, String> {

    @Query("SELECT META().id AS __id, META().cas AS __cas, i.*" +
            " FROM `prop-tax`.`main`.`invoices` i" +
            " WHERE i.tenantId = $1")
    List<Invoice> findAllByTenantId(String tenantId);

    @Query("SELECT META().id AS __id, META().cas AS __cas, i.*" +
            " FROM `prop-tax`.`main`.`invoices` i" +
            " WHERE i.tenantId = $1 AND i.unitId = $2" +
            " ORDER BY i.year DESC, i.month DESC")
    List<Invoice> findByTenantIdAndUnitId(String tenantId, String unitId);

    @Query("SELECT META().id AS __id, META().cas AS __cas, i.*" +
            " FROM `prop-tax`.`main`.`invoices` i" +
            " WHERE i.tenantId = $1 AND i.year = $2 AND i.month = $3" +
            " LIMIT 1")
    List<Invoice> findAnyByTenantIdAndYearAndMonth(String tenantId, int year, int month);

    @Query("SELECT META().id AS __id, META().cas AS __cas, i.*" +
            " FROM `prop-tax`.`main`.`invoices` i" +
            " WHERE i.tenantId = $1 AND i.unitId = $2 AND i.year = $3 AND i.month = $4" +
            " LIMIT 1")
    List<Invoice> findAnyByTenantIdAndUnitIdAndYearAndMonth(String tenantId, String unitId, int year, int month);

    @Query("SELECT META().id AS __id, META().cas AS __cas, i.*" +
            " FROM `prop-tax`.`main`.`invoices` i" +
            " WHERE i.tenantId = $1 AND i.status != 'PAID' AND i.dueDate IS NOT MISSING AND i.dueDate < $2")
    List<Invoice> findOverdueByTenantId(String tenantId, Instant now);
}