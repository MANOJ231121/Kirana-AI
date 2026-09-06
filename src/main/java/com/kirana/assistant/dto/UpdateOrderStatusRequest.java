package com.kirana.assistant.dto;

import jakarta.validation.constraints.NotBlank;

/** PATCH /api/orders/{id}/status request body. */
public class UpdateOrderStatusRequest {

    @NotBlank(message = "Status must not be blank")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
