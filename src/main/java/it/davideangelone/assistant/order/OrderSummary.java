package it.davideangelone.assistant.order;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderSummary(
        Long id,
        String customerName,
        String status,
        BigDecimal amount,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate) {
}
