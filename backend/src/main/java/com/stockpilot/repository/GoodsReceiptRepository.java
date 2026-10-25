package com.stockpilot.repository;

import com.stockpilot.entity.GoodsReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long>, JpaSpecificationExecutor<GoodsReceipt> {

    Optional<GoodsReceipt> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"receivedBy", "warehouse"})
    List<GoodsReceipt> findByPurchaseOrderIdOrderByReceivedAtAsc(Long purchaseOrderId);

    @Override
    @EntityGraph(attributePaths = {"purchaseOrder", "purchaseOrder.supplier", "warehouse", "receivedBy"})
    @NonNull
    Page<GoodsReceipt> findAll(Specification<GoodsReceipt> spec, @NonNull Pageable pageable);

    @EntityGraph(attributePaths = {"purchaseOrder", "purchaseOrder.supplier", "warehouse", "receivedBy"})
    Optional<GoodsReceipt> findDetailedById(Long id);
}
