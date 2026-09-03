package com.kirana.assistant.repository;

import com.kirana.assistant.model.Customer;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CustomerRepository extends MongoRepository<Customer, String> {

    Optional<Customer> findByPhoneNumber(String phoneNumber);
}
