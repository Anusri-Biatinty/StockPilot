package com.stockpilot.repository;

import com.stockpilot.entity.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    @Override
    @EntityGraph(attributePaths = {"product", "fromWarehouse", "toWarehouse", "createdBy"})
    @NonNull
    Page<StockTransfer> findAll(@NonNull Pageable pageable);
}
