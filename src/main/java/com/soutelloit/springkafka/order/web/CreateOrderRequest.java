package com.soutelloit.springkafka.order.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(

        @NotBlank
        @Size(max = 64)
        String customerId,

        @NotBlank
        @Size(max = 120)
        String product,

        @Min(1)
        int quantity,

        @NotNull
        @DecimalMin(value = "0.00")
        BigDecimal amount) {
}
