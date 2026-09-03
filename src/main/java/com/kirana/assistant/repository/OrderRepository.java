package com.kirana.assistant.repository;

import com.kirana.assistant.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    Optional<Order> findFirstByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<Order> findByStatusOrderByCreatedAtDesc(String status);

    List<Order> findAllByOrderByCreatedAtDesc();
}
