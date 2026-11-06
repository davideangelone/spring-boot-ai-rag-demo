package it.davideangelone.assistant.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private static final RowMapper<OrderSummary> ROW_MAPPER = OrderRepository::mapRow;
    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OrderSummary> findByStatus(String status) {
        return jdbcTemplate.query("""
                SELECT id, customer_name, status, amount, order_date, expected_delivery_date
                FROM orders WHERE status = ? ORDER BY expected_delivery_date
                LIMIT 100
                """, ROW_MAPPER, status);
    }

    public Optional<OrderSummary> findById(Long id) {
        List<OrderSummary> rows = jdbcTemplate.query("""
                SELECT id, customer_name, status, amount, order_date, expected_delivery_date
                FROM orders WHERE id = ?
                """, ROW_MAPPER, id);
        return rows.stream().findFirst();
    }

    public BigDecimal sumAmountsByStatus(String status) {
        BigDecimal total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM orders WHERE status = ?", BigDecimal.class, status);
        return total == null ? BigDecimal.ZERO : total;
    }

    private static OrderSummary mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new OrderSummary(
                rs.getLong("id"),
                rs.getString("customer_name"),
                rs.getString("status"),
                rs.getBigDecimal("amount"),
                rs.getDate("order_date").toLocalDate(),
                rs.getDate("expected_delivery_date").toLocalDate());
    }
}
