package com.stockpilot.repository;

import com.stockpilot.entity.InventoryBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long>,
        JpaSpecificationExecutor<InventoryBalance> {

    /**
     * Creates the balance row if it does not exist. Safe under concurrency thanks to ON CONFLICT,
     * so two transactions receiving the first units of a product never collide on the unique key.
     */
    @Modifying
    @Query(value = "INSERT INTO inventory_balances (product_id, warehouse_id) VALUES (:productId, :warehouseId) "
            + "ON CONFLICT (product_id, warehouse_id) DO NOTHING", nativeQuery = true)
    void ensureExists(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);

    /** SELECT ... FOR UPDATE: serialises all stock changes for this product/warehouse pair. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBalance b where b.product.id = :productId and b.warehouse.id = :warehouseId")
    Optional<InventoryBalance> findForUpdate(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);

    @Query("select b from InventoryBalance b join fetch b.warehouse where b.product.id = :productId order by b.warehouse.name")
    List<InventoryBalance> findByProductIdWithWarehouse(@Param("productId") Long productId);

    Optional<InventoryBalance> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    @Query("select coalesce(sum(b.quantityOnHand), 0) from InventoryBalance b where b.product.id = :productId")
    int totalOnHand(@Param("productId") Long productId);

    /** Rows of [productId, onHand, reserved]. */
    @Query("select b.product.id, sum(b.quantityOnHand), sum(b.quantityReserved) from InventoryBalance b "
            + "where b.product.id in :productIds group by b.product.id")
    List<Object[]> totalsByProduct(@Param("productIds") Collection<Long> productIds);

    @Query("select coalesce(sum(b.quantityOnHand), 0) from InventoryBalance b where b.warehouse.id = :warehouseId")
    long totalOnHandInWarehouse(@Param("warehouseId") Long warehouseId);

    @Override
    @EntityGraph(attributePaths = {"product", "product.category", "warehouse"})
    @NonNull
    Page<InventoryBalance> findAll(Specification<InventoryBalance> spec, @NonNull Pageable pageable);
}
