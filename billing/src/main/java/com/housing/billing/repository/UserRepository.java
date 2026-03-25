package com.housing.billing.repository;

import com.housing.billing.model.User;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends CouchbaseRepository<User, String> {

    @Query("SELECT META().id AS __id, META().cas AS __cas, u.*" +
            " FROM `prop-tax`.`main`.`users` u" +
            " WHERE u.email = $1 LIMIT 1")
    Optional<User> findByEmail(String email);
}
