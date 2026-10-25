package com.stockpilot.repository;

import com.stockpilot.entity.ReservationStatus;
import com.stockpilot.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    @Query("select r from StockReservation r where r.salesOrderItem.salesOrder.id = :orderId and r.status = :status "
            + "order by r.product.id")
    List<StockReservation> findByOrderAndStatus(@Param("orderId") Long orderId, @Param("status") ReservationStatus status);
}
