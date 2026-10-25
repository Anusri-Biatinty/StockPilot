package com.stockpilot.service;

import com.stockpilot.dto.CategoryDtos;
import com.stockpilot.entity.Category;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.NotFoundException;
import com.stockpilot.mapper.CatalogMapper;
import com.stockpilot.repository.CategoryRepository;
import com.stockpilot.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CategoryDtos.Response> list() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : categoryRepository.countActiveProductsByCategory()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> CatalogMapper.toResponse(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoryDtos.Response create(CategoryDtos.Request request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("DUPLICATE_CATEGORY", "A category named '" + name + "' already exists");
        }
        Category category = new Category();
        apply(category, request, name);
        categoryRepository.save(category);
        auditService.record("CATEGORY_CREATED", "Category", category.getId(), Map.of("name", name));
        return CatalogMapper.toResponse(category, 0);
    }

    @Transactional
    public CategoryDtos.Response update(Long id, CategoryDtos.Request request) {
        Category category = find(id);
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("DUPLICATE_CATEGORY", "A category named '" + name + "' already exists");
        }
        apply(category, request, name);
        auditService.record("CATEGORY_UPDATED", "Category", id, Map.of("name", name, "active", category.isActive()));
        return CatalogMapper.toResponse(category, productRepository.countByCategoryId(id));
    }

    /** Categories that still have products (active or archived) cannot be deleted; deactivate them instead. */
    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        long products = productRepository.countByCategoryId(id);
        if (products > 0) {
            throw new ConflictException("CATEGORY_IN_USE",
                    "Category has " + products + " product(s). Move them or deactivate the category instead.");
        }
        categoryRepository.delete(category);
        auditService.record("CATEGORY_DELETED", "Category", id, Map.of("name", category.getName()));
    }

    Category find(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> NotFoundException.of("Category", id));
    }

    private static void apply(Category category, CategoryDtos.Request request, String name) {
        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        if (request.active() != null) {
            category.setActive(request.active());
        }
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
