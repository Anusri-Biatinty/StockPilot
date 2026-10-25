package com.stockpilot.repository;

import com.stockpilot.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {

    @Override
    @EntityGraph(attributePaths = {"supplier", "warehouse", "createdBy"})
    @NonNull
    Page<PurchaseOrder> findAll(Specification<PurchaseOrder> spec, @NonNull Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "warehouse", "createdBy", "approvedBy"})
    Optional<PurchaseOrder> findDetailedById(Long id);

    /** Locks the order row so that concurrent receipts / transitions are applied one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select po from PurchaseOrder po where po.id = :id")
    Optional<PurchaseOrder> findForUpdate(@Param("id") Long id);
}
