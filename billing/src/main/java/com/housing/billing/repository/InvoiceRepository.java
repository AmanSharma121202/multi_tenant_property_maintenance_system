package com.housing.billing.repository;

import com.housing.billing.model.Invoice;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

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
            " WHERE i.tenantId = $1 AND i.ownerId = $2" +
            " ORDER BY i.year DESC, i.month DESC")
    List<Invoice> findByTenantIdAndOwnerId(String tenantId, String ownerId);

    @Query("SELECT META().id AS __id, META().cas AS __cas, i.*" +
            " FROM `prop-tax`.`main`.`invoices` i" +
            " WHERE i.tenantId = $1 AND i.status = $2")
    List<Invoice> findByTenantIdAndStatus(String tenantId, String status);

    @Query("SELECT META().id AS __id, META().cas AS __cas, i.*" +
            " FROM `prop-tax`.`main`.`invoices` i" +
            " WHERE i.tenantId = $1 AND i.year = $2")
    List<Invoice> findByTenantIdAndYear(String tenantId, int year);

    @Query("SELECT META().id AS __id, META().cas AS __cas, i.*" +
            " FROM `prop-tax`.`main`.`invoices` i" +
            " WHERE i.tenantId = $1 AND i.year = $2 AND i.month = $3")
    List<Invoice> findByTenantIdAndYearAndMonth(String tenantId, int year, int month);

    @Query("SELECT META().id AS __id, META().cas AS __cas, i.*" +
            " FROM `prop-tax`.`main`.`invoices` i" +
            " WHERE i.tenantId = $1 AND i.status = 'OVERDUE'")
    List<Invoice> findOverdueByTenantId(String tenantId);
}