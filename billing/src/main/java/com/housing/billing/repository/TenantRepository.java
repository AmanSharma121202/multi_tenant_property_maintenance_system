package com.housing.billing.repository;

import com.housing.billing.model.Tenant;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends CouchbaseRepository<Tenant, String> {

    @Query("SELECT META().id AS __id, META().cas AS __cas, t.*" +
            " FROM `prop-tax`.`main`.`tenants` t" +
            " WHERE t.type = 'tenant'")
    List<Tenant> findAllTenants();

    @Query("SELECT META().id AS __id, META().cas AS __cas, t.*" +
            " FROM `prop-tax`.`main`.`tenants` t" +
            " WHERE LOWER(t.name) = LOWER($1) LIMIT 1")
    Optional<Tenant> findByNameIgnoreCase(String name);
}
