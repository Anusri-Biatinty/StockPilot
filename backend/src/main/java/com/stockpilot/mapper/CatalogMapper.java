package com.stockpilot.mapper;

import com.stockpilot.dto.CategoryDtos;
import com.stockpilot.dto.ProductDtos;
import com.stockpilot.dto.StockStatus;
import com.stockpilot.entity.Category;
import com.stockpilot.entity.Product;

public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static CategoryDtos.Response toResponse(Category category, long productCount) {
        return new CategoryDtos.Response(category.getId(), category.getName(), category.getDescription(),
                category.isActive(), productCount);
    }

    public static ProductDtos.Response toResponse(Product p, long onHand, long reserved) {
        return new ProductDtos.Response(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                p.getCategory().getId(), p.getCategory().getName(), p.getUnitPrice(), p.getCostPrice(),
                p.getReorderThreshold(), p.getImageUrl(), p.getStatus(), onHand, reserved, onHand - reserved,
                StockStatus.of(onHand, p.getReorderThreshold()), p.getVersion(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
