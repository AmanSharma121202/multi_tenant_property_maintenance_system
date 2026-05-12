package com.housing.billing.config;

import com.housing.billing.config.converters.InstantToStringConverter;
import com.housing.billing.config.converters.LocalDateToStringConverter;
import com.housing.billing.config.converters.StringToInstantConverter;
import com.housing.billing.config.converters.StringToLocalDateConverter;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.core.convert.CouchbaseCustomConversions;
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

    @Override
    public CouchbaseCustomConversions customConversions() {
        return new CouchbaseCustomConversions(List.of(
                new InstantToStringConverter(),
                new StringToInstantConverter(),
                new LocalDateToStringConverter(),
                new StringToLocalDateConverter()
        ));
    }
}