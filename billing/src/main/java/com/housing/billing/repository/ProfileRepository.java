package com.housing.billing.repository;

import com.housing.billing.model.Profile;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileRepository extends CouchbaseRepository<Profile, String> {

    @Query("SELECT META().id AS __id, META().cas AS __cas, p.*" +
            " FROM `prop-tax`.`main`.`profiles` p" +
            " WHERE p.tenantId = $1")
    List<Profile> findByTenantId(String tenantId);

    @Query("SELECT META().id AS __id, META().cas AS __cas, p.*" +
            " FROM `prop-tax`.`main`.`profiles` p" +
            " WHERE p.tenantId = $1 AND p.code = $2 LIMIT 1")
    Optional<Profile> findByTenantIdAndCode(String tenantId, String code);

    @Query("SELECT META().id AS __id, META().cas AS __cas, p.*" +
            " FROM `prop-tax`.`main`.`profiles` p" +
            " WHERE p.tenantId = $1 AND LOWER(p.code) LIKE LOWER($2)")
    List<Profile> findByTenantIdAndCodeLike(String tenantId, String code);

    @Query("SELECT META().id AS __id, META().cas AS __cas, p.*" +
            " FROM `prop-tax`.`main`.`profiles` p" +
            " WHERE p.tenantId = $1 AND p.active = $2")
    List<Profile> findByTenantIdAndActive(String tenantId, boolean active);
}