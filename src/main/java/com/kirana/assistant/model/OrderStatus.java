package com.kirana.assistant.model;

/**
 * Canonical order lifecycle for the web-based Kirana store.
 *
 * <pre>
 * PENDING → ACCEPTED → PREPARING → READY → COMPLETED
 * PENDING → REJECTED
 * PENDING/ACCEPTED → CANCELLED
 * </pre>
 */
public enum OrderStatus {
    PENDING,
    ACCEPTED,
    PREPARING,
    READY,
    COMPLETED,
    REJECTED,
    CANCELLED
}
