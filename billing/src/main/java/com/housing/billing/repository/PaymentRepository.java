package com.housing.billing.repository;

import com.housing.billing.model.Payment;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PaymentRepository extends CouchbaseRepository<Payment, String> {

    @Query("SELECT META().id AS __id, META().cas AS __cas, p.*" +
            " FROM `prop-tax`.`main`.`payments` p" +
            " WHERE p.tenantId = $1" +
            " ORDER BY p.receivedAt DESC")
    List<Payment> findAllByTenantId(String tenantId);

    @Query("SELECT META().id AS __id, META().cas AS __cas, p.*" +
            " FROM `prop-tax`.`main`.`payments` p" +
            " WHERE p.tenantId = $1 AND p.unitId = $2" +
            " ORDER BY p.receivedAt DESC")
    List<Payment> findByTenantIdAndUnitId(String tenantId, String unitId);

    @Query("SELECT META().id AS __id, META().cas AS __cas, p.*" +
            " FROM `prop-tax`.`main`.`payments` p" +
            " WHERE p.tenantId = $1 AND p.ownerId = $2" +
            " ORDER BY p.receivedAt DESC")
    List<Payment> findByTenantIdAndOwnerId(String tenantId, String ownerId);

    @Query("SELECT META().id AS __id, META().cas AS __cas, p.*" +
            " FROM `prop-tax`.`main`.`payments` p" +
            " WHERE p.tenantId = $1 AND p.invoiceId = $2")
    List<Payment> findByTenantIdAndInvoiceId(String tenantId, String invoiceId);

    @Query("SELECT META().id AS __id, META().cas AS __cas, p.*" +
            " FROM `prop-tax`.`main`.`payments` p" +
            " WHERE p.tenantId = $1 AND UPPER(p.method) = UPPER($2)" +
            " ORDER BY p.receivedAt DESC")
    List<Payment> findByTenantIdAndMethod(String tenantId, String method);
}