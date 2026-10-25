package com.stockpilot.repository;

import com.stockpilot.entity.SupplierProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierProductRepository extends JpaRepository<SupplierProduct, Long> {

    @Query("select sp from SupplierProduct sp join fetch sp.product p where sp.supplier.id = :supplierId order by p.name")
    List<SupplierProduct> findBySupplierIdWithProduct(@Param("supplierId") Long supplierId);

    @Query("select sp from SupplierProduct sp join fetch sp.supplier s where sp.product.id = :productId order by s.name")
    List<SupplierProduct> findByProductIdWithSupplier(@Param("productId") Long productId);

    Optional<SupplierProduct> findBySupplierIdAndProductId(Long supplierId, Long productId);
}
