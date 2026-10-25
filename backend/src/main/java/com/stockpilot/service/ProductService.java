package com.stockpilot.service;

import com.stockpilot.dto.PageResponse;
import com.stockpilot.dto.ProductDtos;
import com.stockpilot.dto.StockStatus;
import com.stockpilot.entity.Category;
import com.stockpilot.entity.InventoryBalance;
import com.stockpilot.entity.Product;
import com.stockpilot.entity.ProductStatus;
import com.stockpilot.exception.BadRequestException;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.NotFoundException;
import com.stockpilot.mapper.CatalogMapper;
import com.stockpilot.repository.InventoryBalanceRepository;
import com.stockpilot.repository.ProductRepository;
import com.stockpilot.repository.SupplierProductRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final CategoryService categoryService;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<ProductDtos.Response> search(String search, Long categoryId, ProductStatus status,
                                                     StockStatus stockStatus, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("sku")), like)));
            }
            if (categoryId != null) {
                p.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (stockStatus != null) {
                Subquery<Long> total = query.subquery(Long.class);
                Root<InventoryBalance> b = total.from(InventoryBalance.class);
                total.select(cb.coalesce(cb.sumAsLong(b.get("quantityOnHand")), 0L))
                        .where(cb.equal(b.get("product"), root));
                var threshold = root.<Integer>get("reorderThreshold").as(Long.class);
                p.add(switch (stockStatus) {
                    case OUT_OF_STOCK -> cb.equal(total, 0L);
                    case LOW_STOCK -> cb.and(cb.greaterThan(total, 0L), cb.lessThanOrEqualTo(total, threshold));
                    case IN_STOCK -> cb.greaterThan(total, threshold);
                });
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<Product> page = productRepository.findAll(spec, pageable);
        Map<Long, long[]> totals = totals(page.getContent().stream().map(Product::getId).toList());
        return PageResponse.of(page, prod -> {
            long[] t = totals.getOrDefault(prod.getId(), new long[2]);
            return CatalogMapper.toResponse(prod, t[0], t[1]);
        });
    }

    @Transactional(readOnly = true)
    public ProductDtos.Detail get(Long id) {
        Product product = productRepository.findWithCategoryById(id).orElseThrow(() -> NotFoundException.of("Product", id));
        List<InventoryBalance> balances = balanceRepository.findByProductIdWithWarehouse(id);
        long onHand = balances.stream().mapToLong(InventoryBalance::getQuantityOnHand).sum();
        long reserved = balances.stream().mapToLong(InventoryBalance::getQuantityReserved).sum();
        var stock = balances.stream()
                .map(b -> new ProductDtos.WarehouseStock(b.getWarehouse().getId(), b.getWarehouse().getCode(),
                        b.getWarehouse().getName(), b.getQuantityOnHand(), b.getQuantityReserved(), b.available()))
                .toList();
        var suppliers = supplierProductRepository.findByProductIdWithSupplier(id).stream()
                .map(sp -> new ProductDtos.SupplierOffer(sp.getSupplier().getId(), sp.getSupplier().getName(),
                        sp.getSupplierSku(), sp.getUnitCost(), sp.getLeadTimeDays()))
                .toList();
        BigDecimal value = product.getCostPrice().multiply(BigDecimal.valueOf(onHand));
        return new ProductDtos.Detail(CatalogMapper.toResponse(product, onHand, reserved), stock, suppliers, value);
    }

    @Transactional
    public ProductDtos.Response create(ProductDtos.Request request) {
        Category category = activeCategory(request.categoryId());
        String sku = request.sku() == null || request.sku().isBlank()
                ? generateUniqueSku(category)
                : normalizeSku(request.sku());
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw new ConflictException("DUPLICATE_SKU", "SKU " + sku + " is already in use");
        }
        Product product = new Product();
        product.setSku(sku);
        apply(product, request, category);
        productRepository.saveAndFlush(product);
        auditService.record("PRODUCT_CREATED", "Product", product.getId(),
                Map.of("sku", sku, "name", product.getName(), "unitPrice", product.getUnitPrice()));
        return CatalogMapper.toResponse(product, 0, 0);
    }

    @Transactional
    public ProductDtos.Response update(Long id, ProductDtos.Request request) {
        Product product = productRepository.findWithCategoryById(id).orElseThrow(() -> NotFoundException.of("Product", id));
        if (request.version() == null) {
            throw new BadRequestException("VERSION_REQUIRED", "version is required when updating a product");
        }
        if (request.version() != product.getVersion()) {
            throw new ConflictException("CONCURRENT_MODIFICATION",
                    "This product was changed by someone else. Reload it and try again.");
        }
        Category category = Objects.equals(product.getCategory().getId(), request.categoryId())
                ? product.getCategory() : activeCategory(request.categoryId());

        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.sku() != null && !request.sku().isBlank()) {
            String sku = normalizeSku(request.sku());
            if (!sku.equals(product.getSku())) {
                if (productRepository.existsBySkuIgnoreCaseAndIdNot(sku, id)) {
                    throw new ConflictException("DUPLICATE_SKU", "SKU " + sku + " is already in use");
                }
                changes.put("sku", product.getSku() + " -> " + sku);
                product.setSku(sku);
            }
        }
        diff(changes, "name", product.getName(), request.name().trim());
        diff(changes, "unitPrice", product.getUnitPrice(), request.unitPrice());
        diff(changes, "costPrice", product.getCostPrice(), request.costPrice());
        diff(changes, "reorderThreshold", product.getReorderThreshold(), request.reorderThreshold());
        diff(changes, "category", product.getCategory().getName(), category.getName());
        apply(product, request, category);
        productRepository.saveAndFlush(product);
        auditService.record("PRODUCT_UPDATED", "Product", id, changes);
        return toResponse(product);
    }

    @Transactional
    public ProductDtos.Response setStatus(Long id, ProductStatus status) {
        Product product = productRepository.findWithCategoryById(id).orElseThrow(() -> NotFoundException.of("Product", id));
        if (product.getStatus() == status) {
            throw new ConflictException("NO_CHANGE", "Product is already " + status.name().toLowerCase());
        }
        product.setStatus(status);
        productRepository.saveAndFlush(product);
        auditService.record(status == ProductStatus.ARCHIVED ? "PRODUCT_ARCHIVED" : "PRODUCT_RESTORED",
                "Product", id, Map.of("sku", product.getSku()));
        return toResponse(product);
    }

    private ProductDtos.Response toResponse(Product product) {
        long[] t = totals(List.of(product.getId())).getOrDefault(product.getId(), new long[2]);
        return CatalogMapper.toResponse(product, t[0], t[1]);
    }

    private Map<Long, long[]> totals(List<Long> ids) {
        Map<Long, long[]> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (Object[] row : balanceRepository.totalsByProduct(ids)) {
            result.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return result;
    }

    private Category activeCategory(Long categoryId) {
        Category category = categoryService.find(categoryId);
        if (!category.isActive()) {
            throw new ConflictException("CATEGORY_INACTIVE", "Category '" + category.getName() + "' is inactive");
        }
        return category;
    }

    private String generateUniqueSku(Category category) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String sku = numberService.nextSku(category.getName());
            if (!productRepository.existsBySkuIgnoreCase(sku)) {
                return sku;
            }
        }
        throw new ConflictException("SKU_GENERATION_FAILED", "Could not generate a unique SKU; please enter one");
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private static void apply(Product product, ProductDtos.Request request, Category category) {
        product.setName(request.name().trim());
        product.setDescription(CategoryService.blankToNull(request.description()));
        product.setCategory(category);
        product.setUnitPrice(request.unitPrice());
        product.setCostPrice(request.costPrice());
        product.setReorderThreshold(request.reorderThreshold());
        product.setImageUrl(CategoryService.blankToNull(request.imageUrl()));
    }

    private static void diff(Map<String, Object> changes, String field, Object before, Object after) {
        boolean equal = before instanceof BigDecimal b && after instanceof BigDecimal a
                ? b.compareTo(a) == 0 : Objects.equals(before, after);
        if (!equal) {
            changes.put(field, before + " -> " + after);
        }
    }
}
