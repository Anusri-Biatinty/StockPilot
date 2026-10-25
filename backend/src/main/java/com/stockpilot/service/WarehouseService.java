package com.stockpilot.service;

import com.stockpilot.dto.WarehouseDtos;
import com.stockpilot.entity.Warehouse;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.NotFoundException;
import com.stockpilot.mapper.InventoryMapper;
import com.stockpilot.repository.InventoryBalanceRepository;
import com.stockpilot.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private record Stats(long products, long units, BigDecimal value) {
    }

    private static final Stats EMPTY = new Stats(0, 0, BigDecimal.ZERO);

    private final WarehouseRepository warehouseRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final AuditService auditService;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<WarehouseDtos.Response> list() {
        Map<Long, Stats> stats = stats(null);
        return warehouseRepository.findAllByOrderByNameAsc().stream()
                .map(w -> toResponse(w, stats.getOrDefault(w.getId(), EMPTY)))
                .toList();
    }

    @Transactional(readOnly = true)
    public WarehouseDtos.Response get(Long id) {
        Warehouse w = find(id);
        return toResponse(w, stats(id).getOrDefault(id, EMPTY));
    }

    @Transactional
    public WarehouseDtos.Response create(WarehouseDtos.Request request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (warehouseRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("DUPLICATE_WAREHOUSE", "Warehouse code " + code + " is already in use");
        }
        Warehouse w = new Warehouse();
        w.setCode(code);
        apply(w, request);
        warehouseRepository.save(w);
        auditService.record("WAREHOUSE_CREATED", "Warehouse", w.getId(), Map.of("code", code, "name", w.getName()));
        return toResponse(w, EMPTY);
    }

    @Transactional
    public WarehouseDtos.Response update(Long id, WarehouseDtos.Request request) {
        Warehouse w = find(id);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (warehouseRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ConflictException("DUPLICATE_WAREHOUSE", "Warehouse code " + code + " is already in use");
        }
        boolean deactivating = w.isActive() && Boolean.FALSE.equals(request.active());
        if (deactivating && balanceRepository.totalOnHandInWarehouse(id) > 0) {
            throw new ConflictException("WAREHOUSE_NOT_EMPTY",
                    "Transfer or issue all stock out of " + w.getCode() + " before deactivating it");
        }
        w.setCode(code);
        apply(w, request);
        auditService.record("WAREHOUSE_UPDATED", "Warehouse", id, Map.of("code", code, "active", w.isActive()));
        return toResponse(w, stats(id).getOrDefault(id, EMPTY));
    }

    private Warehouse find(Long id) {
        return warehouseRepository.findById(id).orElseThrow(() -> NotFoundException.of("Warehouse", id));
    }

    private static void apply(Warehouse w, WarehouseDtos.Request request) {
        w.setName(request.name().trim());
        w.setAddress(CategoryService.blankToNull(request.address()));
        w.setCity(CategoryService.blankToNull(request.city()));
        w.setCountry(CategoryService.blankToNull(request.country()));
        if (request.active() != null) {
            w.setActive(request.active());
        }
    }

    private Map<Long, Stats> stats(Long warehouseId) {
        String sql = """
                SELECT b.warehouse_id,
                       COUNT(*) FILTER (WHERE b.quantity_on_hand > 0) AS products,
                       COALESCE(SUM(b.quantity_on_hand), 0) AS units,
                       COALESCE(SUM(b.quantity_on_hand * p.cost_price), 0) AS value
                FROM inventory_balances b
                JOIN products p ON p.id = b.product_id
                WHERE (CAST(:warehouseId AS BIGINT) IS NULL OR b.warehouse_id = :warehouseId)
                GROUP BY b.warehouse_id
                """;
        Map<Long, Stats> result = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("warehouseId", warehouseId), rs -> {
            result.put(rs.getLong("warehouse_id"),
                    new Stats(rs.getLong("products"), rs.getLong("units"), rs.getBigDecimal("value")));
        });
        return result;
    }

    private static WarehouseDtos.Response toResponse(Warehouse w, Stats s) {
        return InventoryMapper.toResponse(w, s.products(), s.units(), s.value());
    }
}
