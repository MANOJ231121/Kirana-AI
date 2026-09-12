package com.kirana.assistant.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "call_sessions")
public class CallSession {

    @Id
    private String id;

    private String callSid;

    private String customerId;

    private String phoneNumber;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private List<TranscriptEntry> transcript = new ArrayList<>();

    private List<String> orderChanges = new ArrayList<>();

    private String status;

    public CallSession() {
        this.startTime = LocalDateTime.now();
        this.status = "ACTIVE";
    }

    public CallSession(String callSid, String phoneNumber) {
        this.callSid = callSid;
        this.phoneNumber = phoneNumber;
        this.startTime = LocalDateTime.now();
        this.status = "ACTIVE";
    }

    public static class TranscriptEntry {
        private String speaker;
        private String text;
        private LocalDateTime timestamp;

        public TranscriptEntry() {
        }

        public TranscriptEntry(String speaker, String text) {
            this.speaker = speaker;
            this.text = text;
            this.timestamp = LocalDateTime.now();
        }

        public String getSpeaker() {
            return speaker;
        }

        public void setSpeaker(String speaker) {
            this.speaker = speaker;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCallSid() {
        return callSid;
    }

    public void setCallSid(String callSid) {
        this.callSid = callSid;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public List<TranscriptEntry> getTranscript() {
        return transcript;
    }

    public void setTranscript(List<TranscriptEntry> transcript) {
        this.transcript = transcript;
    }

    public void addTranscriptEntry(String speaker, String text) {
        this.transcript.add(new TranscriptEntry(speaker, text));
    }

    public List<String> getOrderChanges() {
        return orderChanges;
    }

    public void setOrderChanges(List<String> orderChanges) {
        this.orderChanges = orderChanges;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
