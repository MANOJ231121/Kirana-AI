package com.kirana.assistant.repository;

import com.kirana.assistant.model.WhatsAppMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface WhatsAppMessageRepository extends MongoRepository<WhatsAppMessage, String> {

    List<WhatsAppMessage> findByCustomerPhoneNumberOrderByTimestampDesc(String phoneNumber);

    Optional<WhatsAppMessage> findFirstByCustomerPhoneNumberOrderByTimestampDesc(String phoneNumber);
}
