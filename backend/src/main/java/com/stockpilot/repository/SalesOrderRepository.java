package com.stockpilot.repository;

import com.stockpilot.entity.SalesOrder;
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

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    @Override
    @EntityGraph(attributePaths = {"customer", "warehouse", "createdBy"})
    @NonNull
    Page<SalesOrder> findAll(Specification<SalesOrder> spec, @NonNull Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "warehouse", "createdBy"})
    Optional<SalesOrder> findDetailedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select so from SalesOrder so where so.id = :id")
    Optional<SalesOrder> findForUpdate(@Param("id") Long id);
}
