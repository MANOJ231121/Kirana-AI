package com.kirana.assistant.repository;

import com.kirana.assistant.model.CallSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CallSessionRepository extends MongoRepository<CallSession, String> {

    Optional<CallSession> findByCallSid(String callSid);

    List<CallSession> findAllByOrderByStartTimeDesc();
}
