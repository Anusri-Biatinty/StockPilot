package com.stockpilot.mapper;

import com.stockpilot.dto.GoodsReceiptDtos;
import com.stockpilot.dto.PurchaseOrderDtos;
import com.stockpilot.dto.SupplierDtos;
import com.stockpilot.entity.GoodsReceipt;
import com.stockpilot.entity.PurchaseOrder;
import com.stockpilot.entity.PurchaseOrderItem;
import com.stockpilot.entity.Supplier;
import com.stockpilot.entity.SupplierProduct;

import java.math.BigDecimal;
import java.util.List;

public final class ProcurementMapper {

    private ProcurementMapper() {
    }

    public static SupplierDtos.Response toResponse(Supplier s, long productCount, long openOrders, BigDecimal spend) {
        return new SupplierDtos.Response(s.getId(), s.getCode(), s.getName(), s.getContactName(), s.getEmail(),
                s.getPhone(), s.getAddress(), s.isActive(), productCount, openOrders, spend);
    }

    public static SupplierDtos.ProductResponse toResponse(SupplierProduct sp) {
        return new SupplierDtos.ProductResponse(sp.getId(), sp.getProduct().getId(), sp.getProduct().getSku(),
                sp.getProduct().getName(), sp.getSupplierSku(), sp.getUnitCost(), sp.getLeadTimeDays());
    }

    public static PurchaseOrderDtos.Summary toSummary(PurchaseOrder po) {
        return new PurchaseOrderDtos.Summary(po.getId(), po.getPoNumber(), po.getSupplier().getId(),
                po.getSupplier().getName(), po.getWarehouse().getId(), po.getWarehouse().getCode(), po.getStatus(),
                po.getOrderDate(), po.getExpectedDate(), po.getTotalAmount(), po.getItems().size(),
                UserMapper.displayName(po.getCreatedBy()), po.getCreatedAt());
    }

    public static PurchaseOrderDtos.Detail toDetail(PurchaseOrder po, List<GoodsReceipt> receipts) {
        return new PurchaseOrderDtos.Detail(po.getId(), po.getPoNumber(), po.getSupplier().getId(),
                po.getSupplier().getName(), po.getWarehouse().getId(), po.getWarehouse().getCode(),
                po.getWarehouse().getName(), po.getStatus(), po.getOrderDate(), po.getExpectedDate(), po.getNotes(),
                po.getTotalAmount(), po.getRejectionReason(), UserMapper.displayName(po.getCreatedBy()),
                po.getCreatedBy() == null ? null : po.getCreatedBy().getId(), po.getSubmittedAt(),
                UserMapper.displayName(po.getApprovedBy()), po.getApprovedAt(), po.getCancelledAt(), po.getCreatedAt(),
                po.getItems().stream().map(ProcurementMapper::toItem).toList(),
                receipts.stream().map(ProcurementMapper::toResponse).toList());
    }

    public static PurchaseOrderDtos.Item toItem(PurchaseOrderItem i) {
        return new PurchaseOrderDtos.Item(i.getId(), i.getProduct().getId(), i.getProduct().getSku(),
                i.getProduct().getName(), i.getQuantityOrdered(), i.getQuantityReceived(), i.remainingQuantity(),
                i.getUnitCost(), i.getLineTotal());
    }

    public static GoodsReceiptDtos.Response toResponse(GoodsReceipt gr) {
        List<GoodsReceiptDtos.Item> items = gr.getItems().stream()
                .map(i -> new GoodsReceiptDtos.Item(i.getProduct().getId(), i.getProduct().getSku(),
                        i.getProduct().getName(), i.getQuantityReceived()))
                .toList();
        int total = items.stream().mapToInt(GoodsReceiptDtos.Item::quantityReceived).sum();
        PurchaseOrder po = gr.getPurchaseOrder();
        return new GoodsReceiptDtos.Response(gr.getId(), gr.getReceiptNumber(), po.getId(), po.getPoNumber(),
                po.getSupplier().getName(), gr.getWarehouse().getCode(), UserMapper.displayName(gr.getReceivedBy()),
                gr.getReceivedAt(), gr.getNotes(), items, total);
    }
}
