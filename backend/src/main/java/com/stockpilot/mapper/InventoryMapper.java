package com.stockpilot.mapper;

import com.stockpilot.dto.InventoryDtos;
import com.stockpilot.dto.WarehouseDtos;
import com.stockpilot.entity.InventoryBalance;
import com.stockpilot.entity.InventoryMovement;
import com.stockpilot.entity.StockTransfer;
import com.stockpilot.entity.Warehouse;

import java.math.BigDecimal;

public final class InventoryMapper {

    private InventoryMapper() {
    }

    public static InventoryDtos.BalanceResponse toResponse(InventoryBalance b) {
        var p = b.getProduct();
        var w = b.getWarehouse();
        return new InventoryDtos.BalanceResponse(b.getId(), p.getId(), p.getSku(), p.getName(),
                p.getCategory().getName(), w.getId(), w.getCode(), w.getName(), b.getQuantityOnHand(),
                b.getQuantityReserved(), b.available(), p.getReorderThreshold(), p.getCostPrice(),
                p.getCostPrice().multiply(BigDecimal.valueOf(b.getQuantityOnHand())));
    }

    public static InventoryDtos.MovementResponse toResponse(InventoryMovement m) {
        return new InventoryDtos.MovementResponse(m.getId(), m.getProduct().getId(), m.getProduct().getSku(),
                m.getProduct().getName(), m.getWarehouse().getId(), m.getWarehouse().getCode(), m.getMovementType(),
                m.getQuantity(), m.getBalanceAfter(), m.getUnitCost(), m.getReferenceType(), m.getReferenceId(),
                m.getReason(), UserMapper.displayName(m.getPerformedBy()), m.getCreatedAt());
    }

    public static InventoryDtos.TransferResponse toResponse(StockTransfer t) {
        return new InventoryDtos.TransferResponse(t.getId(), t.getTransferNumber(), t.getProduct().getId(),
                t.getProduct().getSku(), t.getProduct().getName(), t.getFromWarehouse().getCode(),
                t.getToWarehouse().getCode(), t.getQuantity(), t.getNotes(), UserMapper.displayName(t.getCreatedBy()),
                t.getCreatedAt());
    }

    public static WarehouseDtos.Response toResponse(Warehouse w, long productCount, long units, BigDecimal value) {
        return new WarehouseDtos.Response(w.getId(), w.getCode(), w.getName(), w.getAddress(), w.getCity(),
                w.getCountry(), w.isActive(), productCount, units, value);
    }
}
