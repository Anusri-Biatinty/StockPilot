package com.stockpilot.repository;

import com.stockpilot.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

    boolean existsByCodeIgnoreCase(String code);

    List<Supplier> findByActiveTrueOrderByNameAsc();
}
