package com.kirana.assistant.repository;

import com.kirana.assistant.model.CallSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CallSessionRepository extends MongoRepository<CallSession, String> {

    /**
     * Latest session for a callSid. Used instead of findByCallSid so stale
     * duplicate records (created before the save-order fix) can never turn
     * an otherwise-correct lookup into a fatal exception.
     */
    Optional<CallSession> findTopByCallSidOrderByStartTimeDesc(String callSid);

    List<CallSession> findAllByOrderByStartTimeDesc();
}
