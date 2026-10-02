package com.store.finance.service;

import com.store.finance.dto.FinanceSummaryResponse;
import com.store.order.entity.Order;
import com.store.order.repository.OrderRepository;
import com.store.payment.entity.Payment;
import com.store.payment.entity.PaymentStatus;
import com.store.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinanceService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public FinanceSummaryResponse getSummary() {
        List<Payment> payments = paymentRepository.findAll();
        List<Order> orders = orderRepository.findAll();

        BigDecimal gross = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pending = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paid = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal fulfillment = orders.stream()
                .filter(o -> o.getStatus() != com.store.order.entity.OrderStatus.CANCELLED)
                .flatMap(o -> o.getItems().stream())
                .map(item -> {
                    BigDecimal unit = item.getSupplierUnitCost() == null ? BigDecimal.ZERO : item.getSupplierUnitCost();
                    return unit.multiply(BigDecimal.valueOf(item.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal processingFees = BigDecimal.ZERO;
        BigDecimal adjustments = BigDecimal.ZERO;
        BigDecimal profit = gross.subtract(fulfillment).subtract(processingFees).add(adjustments);

        return FinanceSummaryResponse.builder()
                .grossSales(money(gross))
                .fulfillmentCosts(money(fulfillment))
                .processingFees(money(processingFees))
                .adjustments(money(adjustments))
                .profit(money(profit.max(BigDecimal.ZERO)))
                .pending(money(pending))
                .available(money(paid))
                .paid(money(paid))
                .netRevenue(money(gross.subtract(processingFees).add(adjustments).max(BigDecimal.ZERO)))
                .build();
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
