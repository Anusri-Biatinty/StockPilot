package com.stockpilot.service;

import com.stockpilot.dto.CustomerDtos;
import com.stockpilot.dto.PageResponse;
import com.stockpilot.entity.Customer;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.NotFoundException;
import com.stockpilot.mapper.SalesMapper;
import com.stockpilot.repository.CustomerRepository;
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
public class CustomerService {

    private record Stats(long orders, BigDecimal revenue) {
    }

    private static final Stats EMPTY = new Stats(0, BigDecimal.ZERO);

    private final CustomerRepository customerRepository;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public PageResponse<CustomerDtos.Response> search(String search, Boolean active, Pageable pageable) {
        Specification<Customer> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("city"), "")), like)));
            }
            if (active != null) {
                p.add(cb.equal(root.get("active"), active));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<Customer> page = customerRepository.findAll(spec, pageable);
        Map<Long, Stats> stats = stats(page.getContent().stream().map(Customer::getId).toList());
        return PageResponse.of(page, c -> toResponse(c, stats.getOrDefault(c.getId(), EMPTY)));
    }

    @Transactional(readOnly = true)
    public CustomerDtos.Response get(Long id) {
        return toResponse(find(id), stats(List.of(id)).getOrDefault(id, EMPTY));
    }

    @Transactional
    public CustomerDtos.Response create(CustomerDtos.Request request) {
        String code = request.code() == null || request.code().isBlank()
                ? numberService.next(DocumentNumberService.DocumentType.CUSTOMER)
                : request.code().trim().toUpperCase(Locale.ROOT);
        if (customerRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("DUPLICATE_CUSTOMER", "Customer code " + code + " is already in use");
        }
        Customer c = new Customer();
        c.setCode(code);
        apply(c, request);
        customerRepository.save(c);
        auditService.record("CUSTOMER_CREATED", "Customer", c.getId(), Map.of("code", code, "name", c.getName()));
        return toResponse(c, EMPTY);
    }

    @Transactional
    public CustomerDtos.Response update(Long id, CustomerDtos.Request request) {
        Customer c = find(id);
        apply(c, request);
        auditService.record("CUSTOMER_UPDATED", "Customer", id, Map.of("name", c.getName(), "active", c.isActive()));
        return toResponse(c, stats(List.of(id)).getOrDefault(id, EMPTY));
    }

    Customer find(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> NotFoundException.of("Customer", id));
    }

    private static void apply(Customer c, CustomerDtos.Request r) {
        c.setName(r.name().trim());
        c.setEmail(CategoryService.blankToNull(r.email()));
        c.setPhone(CategoryService.blankToNull(r.phone()));
        c.setAddress(CategoryService.blankToNull(r.address()));
        c.setCity(CategoryService.blankToNull(r.city()));
        c.setCountry(CategoryService.blankToNull(r.country()));
        if (r.active() != null) {
            c.setActive(r.active());
        }
    }

    /** Revenue counts confirmed, shipped and delivered orders (the same definition as the dashboard). */
    private Map<Long, Stats> stats(Collection<Long> ids) {
        Map<Long, Stats> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        String sql = """
                SELECT customer_id, COUNT(*) AS orders, COALESCE(SUM(total_amount), 0) AS revenue
                FROM sales_orders
                WHERE customer_id IN (:ids) AND status IN ('CONFIRMED','SHIPPED','DELIVERED')
                GROUP BY customer_id
                """;
        jdbc.query(sql, new MapSqlParameterSource("ids", ids), rs -> {
            result.put(rs.getLong("customer_id"), new Stats(rs.getLong("orders"), rs.getBigDecimal("revenue")));
        });
        return result;
    }

    private static CustomerDtos.Response toResponse(Customer c, Stats s) {
        return SalesMapper.toResponse(c, s.orders(), s.revenue());
    }
}
