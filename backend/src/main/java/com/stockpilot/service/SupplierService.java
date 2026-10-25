package com.stockpilot.service;

import com.stockpilot.dto.PageResponse;
import com.stockpilot.dto.SupplierDtos;
import com.stockpilot.entity.Product;
import com.stockpilot.entity.Supplier;
import com.stockpilot.entity.SupplierProduct;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.NotFoundException;
import com.stockpilot.mapper.ProcurementMapper;
import com.stockpilot.repository.ProductRepository;
import com.stockpilot.repository.SupplierProductRepository;
import com.stockpilot.repository.SupplierRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private record Stats(long products, long openOrders, BigDecimal spend) {
    }

    private static final Stats EMPTY = new Stats(0, 0, BigDecimal.ZERO);

    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final ProductRepository productRepository;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public PageResponse<SupplierDtos.Response> search(String search, Boolean active, Pageable pageable) {
        Specification<Supplier> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("contactName"), "")), like)));
            }
            if (active != null) {
                p.add(cb.equal(root.get("active"), active));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<Supplier> page = supplierRepository.findAll(spec, pageable);
        Map<Long, Stats> stats = stats(page.getContent().stream().map(Supplier::getId).toList());
        return PageResponse.of(page, s -> toResponse(s, stats.getOrDefault(s.getId(), EMPTY)));
    }

    @Transactional(readOnly = true)
    public SupplierDtos.Response get(Long id) {
        Supplier s = find(id);
        return toResponse(s, stats(List.of(id)).getOrDefault(id, EMPTY));
    }

    @Transactional
    public SupplierDtos.Response create(SupplierDtos.Request request) {
        String code = request.code() == null || request.code().isBlank()
                ? numberService.next(DocumentNumberService.DocumentType.SUPPLIER)
                : request.code().trim().toUpperCase(Locale.ROOT);
        if (supplierRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("DUPLICATE_SUPPLIER", "Supplier code " + code + " is already in use");
        }
        Supplier s = new Supplier();
        s.setCode(code);
        apply(s, request);
        supplierRepository.save(s);
        auditService.record("SUPPLIER_CREATED", "Supplier", s.getId(), Map.of("code", code, "name", s.getName()));
        return toResponse(s, EMPTY);
    }

    /** The supplier code is an immutable business key and is not changed on update. */
    @Transactional
    public SupplierDtos.Response update(Long id, SupplierDtos.Request request) {
        Supplier s = find(id);
        apply(s, request);
        auditService.record("SUPPLIER_UPDATED", "Supplier", id, Map.of("name", s.getName(), "active", s.isActive()));
        return toResponse(s, stats(List.of(id)).getOrDefault(id, EMPTY));
    }

    @Transactional(readOnly = true)
    public List<SupplierDtos.ProductResponse> products(Long supplierId) {
        find(supplierId);
        return supplierProductRepository.findBySupplierIdWithProduct(supplierId).stream()
                .map(ProcurementMapper::toResponse).toList();
    }

    /** Adds the product to the supplier's catalogue, or updates the existing entry. */
    @Transactional
    public SupplierDtos.ProductResponse upsertProduct(Long supplierId, SupplierDtos.ProductRequest request) {
        Supplier supplier = find(supplierId);
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> NotFoundException.of("Product", request.productId()));
        SupplierProduct sp = supplierProductRepository.findBySupplierIdAndProductId(supplierId, product.getId())
                .orElseGet(() -> {
                    SupplierProduct created = new SupplierProduct();
                    created.setSupplier(supplier);
                    created.setProduct(product);
                    return created;
                });
        sp.setSupplierSku(CategoryService.blankToNull(request.supplierSku()));
        sp.setUnitCost(request.unitCost());
        sp.setLeadTimeDays(request.leadTimeDays());
        supplierProductRepository.save(sp);
        auditService.record("SUPPLIER_PRODUCT_SAVED", "Supplier", supplierId,
                Map.of("sku", product.getSku(), "unitCost", request.unitCost()));
        return ProcurementMapper.toResponse(sp);
    }

    @Transactional
    public void removeProduct(Long supplierId, Long productId) {
        SupplierProduct sp = supplierProductRepository.findBySupplierIdAndProductId(supplierId, productId)
                .orElseThrow(() -> new NotFoundException("Product " + productId + " is not supplied by supplier " + supplierId));
        supplierProductRepository.delete(sp);
        auditService.record("SUPPLIER_PRODUCT_REMOVED", "Supplier", supplierId, Map.of("productId", productId));
    }

    Supplier find(Long id) {
        return supplierRepository.findById(id).orElseThrow(() -> NotFoundException.of("Supplier", id));
    }

    private static void apply(Supplier s, SupplierDtos.Request request) {
        s.setName(request.name().trim());
        s.setContactName(CategoryService.blankToNull(request.contactName()));
        s.setEmail(CategoryService.blankToNull(request.email()));
        s.setPhone(CategoryService.blankToNull(request.phone()));
        s.setAddress(CategoryService.blankToNull(request.address()));
        if (request.active() != null) {
            s.setActive(request.active());
        }
    }

    /** Total spend counts the value actually received, not the value ordered. */
    private Map<Long, Stats> stats(Collection<Long> ids) {
        Map<Long, Stats> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        String sql = """
                SELECT s.id,
                       (SELECT COUNT(*) FROM supplier_products sp WHERE sp.supplier_id = s.id) AS products,
                       (SELECT COUNT(*) FROM purchase_orders po WHERE po.supplier_id = s.id
                          AND po.status IN ('DRAFT','SUBMITTED','APPROVED','PARTIALLY_RECEIVED')) AS open_orders,
                       (SELECT COALESCE(SUM(i.quantity_received * i.unit_cost), 0)
                          FROM purchase_order_items i JOIN purchase_orders po ON po.id = i.purchase_order_id
                          WHERE po.supplier_id = s.id) AS spend
                FROM suppliers s WHERE s.id IN (:ids)
                """;
        jdbc.query(sql, new MapSqlParameterSource("ids", ids), rs -> {
            result.put(rs.getLong("id"), new Stats(rs.getLong("products"), rs.getLong("open_orders"),
                    rs.getBigDecimal("spend")));
        });
        return result;
    }

    private static SupplierDtos.Response toResponse(Supplier s, Stats st) {
        return ProcurementMapper.toResponse(s, st.products(), st.openOrders(), st.spend());
    }
}
