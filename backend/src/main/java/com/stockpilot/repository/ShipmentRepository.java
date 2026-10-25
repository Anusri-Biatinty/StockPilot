package com.stockpilot.repository;

import com.stockpilot.entity.Shipment;
import com.stockpilot.entity.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findBySalesOrderId(Long salesOrderId);

    @EntityGraph(attributePaths = {"salesOrder", "salesOrder.customer"})
    Page<Shipment> findByStatusIn(Collection<ShipmentStatus> statuses, Pageable pageable);
}
