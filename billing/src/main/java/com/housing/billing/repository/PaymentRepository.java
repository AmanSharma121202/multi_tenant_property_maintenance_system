package com.housing.billing.repository;

import com.housing.billing.model.Payment;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends CouchbaseRepository<Payment, String> {

    @Query("SELECT META().id AS __id, META().cas AS __cas, p.*" +
            " FROM `prop-tax`.`main`.`payments` p" +
            " WHERE p.tenantId = $1" +
            " ORDER BY p.receivedAt DESC")
    List<Payment> findAllByTenantId(String tenantId);

    @Query("SELECT META().id AS __id, META().cas AS __cas, p.*" +
            " FROM `prop-tax`.`main`.`payments` p" +
            " WHERE p.tenantId = $1 AND p.unitId = $2 LIMIT 1")
    Optional<Payment> findAnyByTenantIdAndUnitId(String tenantId, String unitId);

}