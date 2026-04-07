package com.housing.billing.repository;

import com.housing.billing.model.Owner;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface OwnerRepository extends CouchbaseRepository<Owner, String> {

    @Query("SELECT META().id AS __id, META().cas AS __cas, o.*" +
            " FROM `prop-tax`.`main`.`owners` o" +
            " WHERE o.tenantId = $1")
    List<Owner> findByTenantId(String tenantId);

    @Query("SELECT META().id AS __id, META().cas AS __cas, o.*" +
            " FROM `prop-tax`.`main`.`owners` o" +
            " WHERE o.tenantId = $1 AND LOWER(o.name) LIKE LOWER($2)")
    List<Owner> findByTenantIdAndName(String tenantId, String name);

    @Query("SELECT META().id AS __id, META().cas AS __cas, o.*" +
            " FROM `prop-tax`.`main`.`owners` o" +
            " WHERE o.tenantId = $1 AND LOWER(o.email) LIKE LOWER($2)")
    List<Owner> findByTenantIdAndEmail(String tenantId, String email);

    @Query("SELECT META().id AS __id, META().cas AS __cas, o.*" +
            " FROM `prop-tax`.`main`.`owners` o" +
            " WHERE o.tenantId = $1 AND LOWER(o.email) = LOWER($2) LIMIT 1")
    Optional<Owner> findByTenantIdAndEmailIgnoreCase(String tenantId, String email);

    @Query("SELECT META().id AS __id, META().cas AS __cas, o.*" +
            " FROM `prop-tax`.`main`.`owners` o" +
            " WHERE o.tenantId = $1 AND o.phone LIKE $2")
    List<Owner> findByTenantIdAndPhone(String tenantId, String phone);

    @Query("SELECT META().id AS __id, META().cas AS __cas, o.*" +
            " FROM `prop-tax`.`main`.`owners` o" +
            " WHERE o.tenantId = $1 AND o.status = $2")
    List<Owner> findByTenantIdAndStatus(String tenantId, String status);
}