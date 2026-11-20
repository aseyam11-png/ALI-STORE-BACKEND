package com.store.inventory.service;

import com.store.common.exception.BusinessException;
import com.store.inventory.repository.InventoryRepository;
import com.store.inventory.repository.InventoryReservationRepository;
import com.store.order.entity.OrderItem;
import com.store.product.entity.Product;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InventoryServiceReservationTest {

    private InventoryRepository inventoryRepository;
    private InventoryReservationRepository reservationRepository;
    private EntityManager entityManager;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(InventoryRepository.class);
        reservationRepository = mock(InventoryReservationRepository.class);
        entityManager = mock(EntityManager.class);
        inventoryService = new InventoryService(inventoryRepository, reservationRepository);
        ReflectionTestUtils.setField(inventoryService, "entityManager", entityManager);
    }

    @Test
    void refusesReservationWhenProductDoesNotMatchOrderLine() {
        Long orderItemId = 41L;
        Product product = Product.builder().name("Test product").build();
        product.setId(77L);
        OrderItem orderItem = OrderItem.builder().product(product).quantity(2).build();
        orderItem.setId(orderItemId);
        when(entityManager.find(OrderItem.class, orderItemId)).thenReturn(orderItem);

        assertThrows(BusinessException.class,
                () -> inventoryService.reserveStock(88L, 2, orderItemId));
        verifyNoInteractions(inventoryRepository, reservationRepository);
    }

    @Test
    void refusesReservationWhenQuantityDoesNotMatchOrderLine() {
        Long orderItemId = 42L;
        Product product = Product.builder().name("Test product").build();
        product.setId(77L);
        OrderItem orderItem = OrderItem.builder().product(product).quantity(2).build();
        orderItem.setId(orderItemId);
        when(entityManager.find(OrderItem.class, orderItemId)).thenReturn(orderItem);

        assertThrows(BusinessException.class,
                () -> inventoryService.reserveStock(77L, 3, orderItemId));
        verifyNoInteractions(inventoryRepository, reservationRepository);
    }
}
