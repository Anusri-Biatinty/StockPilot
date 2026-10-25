package com.stockpilot.repository;

import com.stockpilot.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Category> findAllByOrderByNameAsc();

    /** Rows of [categoryId, productCount] for non-archived products. */
    @Query("select p.category.id, count(p) from Product p where p.status = com.stockpilot.entity.ProductStatus.ACTIVE group by p.category.id")
    List<Object[]> countActiveProductsByCategory();
}
