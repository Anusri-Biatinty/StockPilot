package com.stockpilot.unit;

import com.stockpilot.entity.InventoryBalance;
import com.stockpilot.entity.InventoryMovement;
import com.stockpilot.entity.MovementType;
import com.stockpilot.entity.NotificationType;
import com.stockpilot.entity.Product;
import com.stockpilot.entity.Warehouse;
import com.stockpilot.exception.BadRequestException;
import com.stockpilot.exception.InsufficientStockException;
import com.stockpilot.repository.InventoryBalanceRepository;
import com.stockpilot.repository.InventoryMovementRepository;
import com.stockpilot.service.InventoryService;
import com.stockpilot.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock InventoryBalanceRepository balanceRepository;
    @Mock InventoryMovementRepository movementRepository;
    @Mock NotificationService notificationService;
    @Mock com.stockpilot.repository.StockTransferRepository transferRepository;
    @Mock com.stockpilot.repository.ProductRepository productRepository;
    @Mock com.stockpilot.repository.WarehouseRepository warehouseRepository;
    @Mock com.stockpilot.repository.UserRepository userRepository;
    @Mock com.stockpilot.service.DocumentNumberService numberService;
    @Mock com.stockpilot.service.AuditService auditService;
    @Mock org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;

    @InjectMocks InventoryService service;

    private Product product;
    private Warehouse warehouse;
    private InventoryBalance balance;

    @BeforeEach
    void setUp() {
        product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setSku("SKU-1");
        product.setName("Widget");
        product.setCostPrice(new BigDecimal("4.00"));
        product.setReorderThreshold(10);

        warehouse = new Warehouse();
        ReflectionTestUtils.setField(warehouse, "id", 2L);
        warehouse.setCode("WH");

        balance = new InventoryBalance();
        balance.setProduct(product);
        balance.setWarehouse(warehouse);
        balance.setQuantityOnHand(20);
        balance.setQuantityReserved(5);
        lenient().when(balanceRepository.findForUpdate(1L, 2L)).thenReturn(Optional.of(balance));
    }

    @Test
    void decreaseIsLimitedToUnreservedStock() {
        assertThatThrownBy(() -> service.decrease(product, warehouse, 16, MovementType.ISSUE, "MANUAL", null, "x"))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 16, available 15");
        assertThat(balance.getQuantityOnHand()).isEqualTo(20);
        verify(movementRepository, never()).save(any());
    }

    @Test
    void decreaseWritesSignedMovementWithBalanceAfter() {
        when(balanceRepository.totalOnHand(1L)).thenReturn(20);
        service.decrease(product, warehouse, 4, MovementType.ISSUE, "MANUAL", null, "Samples");

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(captor.capture());
        InventoryMovement movement = captor.getValue();
        assertThat(movement.getQuantity()).isEqualTo(-4);
        assertThat(movement.getBalanceAfter()).isEqualTo(16);
        assertThat(movement.getUnitCost()).isEqualByComparingTo("4.00");
        assertThat(balance.getQuantityOnHand()).isEqualTo(16);
    }

    @Test
    void reserveRejectsMoreThanAvailableAndKeepsReservationUnchanged() {
        assertThatThrownBy(() -> service.reserve(product, warehouse, 16)).isInstanceOf(InsufficientStockException.class);
        assertThat(balance.getQuantityReserved()).isEqualTo(5);
        service.reserve(product, warehouse, 15);
        assertThat(balance.getQuantityReserved()).isEqualTo(20);
    }

    @Test
    void consumeReservationReducesBothReservedAndOnHand() {
        when(balanceRepository.totalOnHand(1L)).thenReturn(20);
        service.consumeReservation(product, warehouse, 5, 99L);
        assertThat(balance.getQuantityReserved()).isZero();
        assertThat(balance.getQuantityOnHand()).isEqualTo(15);
    }

    @Test
    void nonPositiveQuantitiesAreRejected() {
        assertThatThrownBy(() -> service.increase(product, warehouse, 0, MovementType.RECEIPT, "MANUAL", null, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.decrease(product, warehouse, -1, MovementType.ISSUE, "MANUAL", null, "x"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void notifiesOnlyWhenCrossingTheReorderThreshold() {
        when(balanceRepository.totalOnHand(1L)).thenReturn(20, 10);
        service.decrease(product, warehouse, 10, MovementType.ISSUE, "MANUAL", null, "x"); // 20 -> 10: crosses
        service.decrease(product, warehouse, 5, MovementType.ISSUE, "MANUAL", null, "x");  // 10 -> 5: already low

        verify(notificationService).notifyRoles(any(), eq(NotificationType.LOW_STOCK), any(), anyString(), anyString(), anyString());
    }

    @Test
    void notifiesOutOfStockWhenReachingZero() {
        balance.setQuantityReserved(0);
        when(balanceRepository.totalOnHand(1L)).thenReturn(20);
        service.decrease(product, warehouse, 20, MovementType.ISSUE, "MANUAL", null, "x");
        verify(notificationService).notifyRoles(any(), eq(NotificationType.OUT_OF_STOCK), any(), anyString(), anyString(), anyString());
    }
}
