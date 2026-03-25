package com.housing.billing.repository;

import com.housing.billing.model.Unit;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UnitRepository extends CouchbaseRepository<Unit, String> {

    @Query("SELECT META().id AS __id, META().cas AS __cas, u.*" +
            " FROM `prop-tax`.`main`.`units` u" +
            " WHERE u.tenantId = $1")
    List<Unit> findByTenantId(String tenantId);

    @Query("SELECT META().id AS __id, META().cas AS __cas, u.*" +
            " FROM `prop-tax`.`main`.`units` u" +
            " WHERE u.tenantId = $1 AND LOWER(u.unitNumber) LIKE LOWER($2)")
    List<Unit> findByTenantIdAndUnitNumber(String tenantId, String unitNumber);

    @Query("SELECT META().id AS __id, META().cas AS __cas, u.*" +
            " FROM `prop-tax`.`main`.`units` u" +
            " WHERE u.tenantId = $1 AND u.profileCode = $2")
    List<Unit> findByTenantIdAndProfileCode(String tenantId, String profileCode);

    @Query("SELECT META().id AS __id, META().cas AS __cas, u.*" +
            " FROM `prop-tax`.`main`.`units` u" +
            " WHERE u.tenantId = $1 AND u.active = $2")
    List<Unit> findByTenantIdAndActive(String tenantId, boolean active);

    @Query("SELECT META().id AS __id, META().cas AS __cas, u.*" +
            " FROM `prop-tax`.`main`.`units` u" +
            " WHERE u.tenantId = $1 AND u.ownerId = $2")
    List<Unit> findByTenantIdAndOwnerId(String tenantId, String ownerId);
}