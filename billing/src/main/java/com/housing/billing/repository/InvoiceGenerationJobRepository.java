package com.housing.billing.repository;

import com.housing.billing.model.InvoiceGenerationJob;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceGenerationJobRepository extends CouchbaseRepository<InvoiceGenerationJob, String> {

    @Query("SELECT META().id AS __id, META().cas AS __cas, j.*" +
            " FROM `prop-tax`.`main`.`invoice_generation_jobs` j" +
            " WHERE j.tenantId = $1" +
            " ORDER BY j.createdAt DESC")
    List<InvoiceGenerationJob> findAllByTenantId(String tenantId);
}

