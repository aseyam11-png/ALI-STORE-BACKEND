package com.store.payment;

import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.common.exception.ConflictException;
import com.store.inventory.entity.Inventory;
import com.store.inventory.enums.InventorySource;
import com.store.inventory.repository.InventoryRepository;
import com.store.order.entity.Order;
import com.store.order.entity.OrderItem;
import com.store.order.entity.OrderStatus;
import com.store.order.repository.OrderRepository;
import com.store.order.service.OrderService;
import com.store.payment.dto.PaymentRequest;
import com.store.payment.entity.Payment;
import com.store.payment.entity.PaymentCollectionStatus;
import com.store.payment.entity.PaymentMethod;
import com.store.payment.entity.PaymentStatus;
import com.store.payment.entity.RefundStatus;
import com.store.payment.provider.PaymentProvider;
import com.store.payment.repository.PaymentRepository;
import com.store.payment.repository.RefundRepository;
import com.store.payment.service.PaymentReconciliationService;
import com.store.payment.service.PaymentService;
import com.store.payment.service.RefundService;
import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
import com.store.supplier.entity.Supplier;
import com.store.supplier.repository.SupplierRepository;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.repository.RoleRepository;
import com.store.user.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import org.mockito.ArgumentCaptor;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentFinancialIntegrityTest {

    @Autowired private PaymentService paymentService;
    @Autowired private PaymentReconciliationService paymentReconciliationService;
    @Autowired private RefundService refundService;
    @Autowired private OrderService orderService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    @MockBean private PaymentProvider paymentProvider;

    @Test
    void failedCardPayment_canRetrySameOrderWithoutCreatingAnotherPayment() {
        User user = user();
        Product product = product("FIN-RETRY", "FIN-RETRY-SUP");
        Order order = order(user, product, new BigDecimal("25.00"));

        PaymentRequest request = new PaymentRequest();
        request.setOrderId(order.getId());
        request.setAmount(new BigDecimal("25.00"));
        request.setMethod(PaymentMethod.CREDIT_CARD);

        when(paymentProvider.charge(
                eq(order.getId()),
                eq(new BigDecimal("25.00")),
                eq(PaymentMethod.CREDIT_CARD),
                any()))
                .thenReturn(new PaymentProvider.PaymentResult(false, null, "card declined"))
                .thenReturn(new PaymentProvider.PaymentResult(true, "TXN-RETRY-1", "approved"));

        var first = paymentService.processPayment(user.getEmail(), request);
        Assertions.assertEquals(PaymentStatus.FAILED, first.getStatus());
        Assertions.assertEquals(OrderStatus.PENDING,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        var second = paymentService.processPayment(user.getEmail(), request);
        Assertions.assertEquals(PaymentStatus.COMPLETED, second.getStatus());
        Assertions.assertEquals("TXN-RETRY-1", second.getTransactionId());
        Assertions.assertEquals(1L,
                paymentRepository.findAll().stream()
                        .filter(payment -> payment.getOrderId().equals(order.getId()))
                        .count());
        Assertions.assertEquals(OrderStatus.PROCESSING,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());

        TestTransaction.flagForRollback();
        TestTransaction.end();

        ArgumentCaptor<String> attemptKeyCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(paymentProvider, times(2)).charge(
                eq(order.getId()),
                eq(new BigDecimal("25.00")),
                eq(PaymentMethod.CREDIT_CARD),
                attemptKeyCaptor.capture());

        Assertions.assertEquals("order-" + order.getId(), attemptKeyCaptor.getAllValues().get(0));
        Assertions.assertNotEquals(
                attemptKeyCaptor.getAllValues().get(0),
                attemptKeyCaptor.getAllValues().get(1));
    }

    @Test
    void providerException_persistsReconciliationState() {
        User user = user();
        Product product = product("FIN-RECON", "FIN-RECON-SUP");
        Order order = order(user, product, new BigDecimal("25.00"));

        when(paymentProvider.charge(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("gateway timeout"));

        PaymentRequest request = new PaymentRequest();
        request.setOrderId(order.getId());
        request.setAmount(new BigDecimal("25.00"));
        request.setMethod(PaymentMethod.CREDIT_CARD);

        Assertions.assertThrows(
                ConflictException.class,
                () -> paymentService.processPayment(user.getEmail(), request));

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        Assertions.assertEquals(PaymentStatus.REQUIRES_RECONCILIATION, payment.getStatus());
        Assertions.assertEquals("order-" + order.getId(), payment.getIdempotencyKey());
        Assertions.assertTrue(payment.getProviderError().contains("gateway timeout"));
    }

    @Test
    void reconciliation_doesNotCompletePaymentWithoutTransactionId() {
        User user = user();
        Product product = product("FIN-RECON-NO-TXN", "FIN-RECON-NO-TXN-SUP");
        Order order = order(user, product, new BigDecimal("25.00"));

        Payment payment = paymentRepository.save(Payment.builder()
                .orderId(order.getId())
                .amount(new BigDecimal("25.00"))
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.REQUIRES_RECONCILIATION)
                .idempotencyKey("order-" + order.getId())
                .collectionStatus(PaymentCollectionStatus.NOT_REQUIRED)
                .build());

        when(paymentProvider.getChargeStatus(order.getId(), null))
                .thenReturn(new PaymentProvider.ChargeStatusResult(
                        true, false, "", "completed without transaction id"));

        var response = paymentReconciliationService.reconcile(payment.getId());

        Assertions.assertEquals(PaymentStatus.REQUIRES_RECONCILIATION, response.getStatus());
        Assertions.assertTrue(response.getTransactionId() == null || response.getTransactionId().isBlank());
        Assertions.assertEquals(OrderStatus.PENDING,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void codCollection_requiresDeliveredOrderAndIsIdempotent() {
        User user = user();
        Order order = orderRepository.save(Order.builder()
                .user(user)
                .status(OrderStatus.PROCESSING)
                .totalAmount(new BigDecimal("40.00"))
                .currency("USD")
                .shippingAddress("1 Main St")
                .reservationExpiresAt(LocalDateTime.now().plusMinutes(30))
                .build());

        Payment payment = paymentRepository.save(Payment.builder()
                .orderId(order.getId())
                .amount(new BigDecimal("40.00"))
                .method(PaymentMethod.CASH_ON_DELIVERY)
                .status(PaymentStatus.PENDING)
                .idempotencyKey("order-" + order.getId())
                .collectionStatus(PaymentCollectionStatus.PENDING)
                .build());

        Assertions.assertThrows(
                ConflictException.class,
                () -> paymentService.confirmCodCollectionByPaymentId(payment.getId()));

        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);

        var first = paymentService.confirmCodCollectionByPaymentId(payment.getId());
        var second = paymentService.confirmCodCollectionByPaymentId(payment.getId());

        Assertions.assertEquals(PaymentStatus.COMPLETED, first.getStatus());
        Assertions.assertEquals(PaymentCollectionStatus.COLLECTED, first.getCollectionStatus());
        Assertions.assertEquals(first.getId(), second.getId());
    }

    @Test
    void paidCancellation_createsOneRefundIntent() {
        User user = user();
        Product product = product("FIN-CANCEL", "FIN-CANCEL-SUP");
        Order order = order(user, product, new BigDecimal("25.00"));

        paymentRepository.save(Payment.builder()
                .orderId(order.getId())
                .amount(new BigDecimal("25.00"))
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.COMPLETED)
                .transactionId("TXN-CANCEL-1")
                .idempotencyKey("order-" + order.getId())
                .collectionStatus(PaymentCollectionStatus.NOT_REQUIRED)
                .build());

        orderService.updateOrderStatus(order.getId(), OrderStatus.CANCELLED);

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        Assertions.assertEquals(PaymentStatus.REFUND_PENDING, payment.getStatus());

        var refund = refundRepository.findByIdempotencyKey(
                "order:" + order.getId() + ":cancellation").orElseThrow();
        Assertions.assertEquals(RefundStatus.PENDING, refund.getStatus());

        var sameRefund = refundService.prepareOrderCancellationRefund(
                order.getId(), "retry");
        Assertions.assertEquals(refund.getId(), sameRefund.getId());
    }

    private User user() {
        Role role = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_USER").description("Test role").build()));
        return userRepository.save(User.builder()
                .firstName("Finance")
                .lastName("Test")
                .email("finance-" + System.nanoTime() + "@example.com")
                .password("test-password")
                .isEnabled(true)
                .role(role)
                .build());
    }

    private Category category() {
        return categoryRepository.save(Category.builder()
                .name("Finance Category " + System.nanoTime())
                .slug("finance-" + System.nanoTime())
                .active(true)
                .build());
    }

    private Supplier supplier(String code) {
        return supplierRepository.save(Supplier.builder()
                .externalCode(code)
                .name(code)
                .apiBaseUrl("https://supplier.example")
                .fulfillmentEnabled(true)
                .inventorySyncEnabled(true)
                .build());
    }

    private Product product(String sku, String supplierCode) {
        Category category = category();
        Supplier supplier = supplier(supplierCode);
        Product product = productRepository.save(Product.builder()
                .name("Product " + sku)
                .slug("product-" + sku.toLowerCase())
                .price(new BigDecimal("25.00"))
                .currency("USD")
                .sku(sku)
                .status(ProductStatus.ACTIVE)
                .category(category)
                .supplier(supplier)
                .supplierSku("SUP-" + sku)
                .supplierCost(new BigDecimal("10.00"))
                .build());

        inventoryRepository.save(Inventory.builder()
                .product(product)
                .quantity(10)
                .reservedQuantity(1)
                .lowStockThreshold(1)
                .source(InventorySource.MANUAL)
                .lastSyncedAt(LocalDateTime.now())
                .build());
        return product;
    }

    private Order order(User user, Product product, BigDecimal amount) {
        Order order = orderRepository.save(Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .totalAmount(amount)
                .currency("USD")
                .shippingAddress("1 Main St")
                .reservationExpiresAt(LocalDateTime.now().plusMinutes(30))
                .build());

        order.getItems().add(OrderItem.builder()
                .order(order)
                .product(product)
                .supplier(product.getSupplier())
                .supplierSku(product.getSupplierSku())
                .supplierUnitCost(product.getSupplierCost())
                .quantity(1)
                .unitPrice(amount)
                .subtotal(amount)
                .build());

        return orderRepository.save(order);
    }
}
