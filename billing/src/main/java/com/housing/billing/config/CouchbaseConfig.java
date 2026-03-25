package com.housing.billing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories;

@Configuration
@EnableCouchbaseRepositories(basePackages = "com.housing.billing.repository")
public class CouchbaseConfig extends AbstractCouchbaseConfiguration {

    @Override
    public String getConnectionString() {
        return "couchbase://localhost";
    }

    @Override
    public String getUserName() {
        return "Administrator";
    }

    @Override
    public String getPassword() {
        return "Aman@2003";
    }

    @Override
    public String getBucketName() {
        return "prop-tax";
    }

    @Override
    public String getScopeName() {
        return "main";
    }

    @Override
    protected boolean autoIndexCreation() {
        return false;
    }
}