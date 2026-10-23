package it.davideangelone.assistant.tools;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import it.davideangelone.assistant.order.OrderRepository;
import it.davideangelone.assistant.order.OrderSummary;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class OrderTools implements AiTool {

    private final OrderRepository orderRepository;

    public OrderTools(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Tool(description = "Cerca gli ordini per stato. Stati ammessi: NEW, IN_PROGRESS, SHIPPED, CANCELLED. Se lo stato non è specificato, usa IN_PROGRESS.")
    public List<OrderSummary> findOrdersByStatus(
            @ToolParam(description = "Stato dell'ordine: NEW, IN_PROGRESS, SHIPPED oppure CANCELLED") String status) {
        String normalized = status == null || status.isBlank() ? "IN_PROGRESS" : status.trim().toUpperCase();
        if (!List.of("NEW", "IN_PROGRESS", "SHIPPED", "CANCELLED").contains(normalized)) {
            return List.of();
        }
        return orderRepository.findByStatus(normalized);
    }

    @Tool(description = "Recupera i dati di un ordine tramite il suo identificativo numerico.")
    public Optional<OrderSummary> findOrderById(
            @ToolParam(description = "Identificativo numerico dell'ordine") Long orderId) {
        if (orderId == null || orderId <= 0) {
            return Optional.empty();
        }
        return orderRepository.findById(orderId);
    }

    @Tool(description = "Calcola la somma degli importi degli ordini in uno specifico stato. Stati ammessi: NEW, IN_PROGRESS, SHIPPED, CANCELLED.")
    public BigDecimal sumOrderAmountsByStatus(
            @ToolParam(description = "Stato dell'ordine: NEW, IN_PROGRESS, SHIPPED oppure CANCELLED") String status) {
        String normalized = status == null || status.isBlank() ? "IN_PROGRESS" : status.trim().toUpperCase();
        if (!List.of("NEW", "IN_PROGRESS", "SHIPPED", "CANCELLED").contains(normalized)) {
            return BigDecimal.ZERO;
        }
        return orderRepository.sumAmountsByStatus(normalized);
    }
}
