package com.stockpilot.repository;

import com.stockpilot.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;

import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long>,
        JpaSpecificationExecutor<InventoryMovement> {

    @Override
    @EntityGraph(attributePaths = {"product", "warehouse", "performedBy"})
    @NonNull
    Page<InventoryMovement> findAll(Specification<InventoryMovement> spec, @NonNull Pageable pageable);

    @EntityGraph(attributePaths = {"product", "warehouse", "performedBy"})
    List<InventoryMovement> findTop10ByOrderByCreatedAtDescIdDesc();

    List<InventoryMovement> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
}
