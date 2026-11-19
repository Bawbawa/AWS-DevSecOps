package com.core.warehouseservice.service;

import com.core.warehouseservice.domain.Order;
import com.core.warehouseservice.domain.OrderStatus;
import com.core.warehouseservice.domain.OrderWarehouseStatus;
import com.core.warehouseservice.dto.*;
import com.core.warehouseservice.exceptions.OrderLockedException;
import com.core.warehouseservice.exceptions.OrderNotFoundException;
import com.core.warehouseservice.mapper.OrderMapper;
import com.core.warehouseservice.repository.OrderRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderRepository orderRepository;
    private final SNSPublisherService snsPublisherService;

    private static final long PICKING_ORDER_LOCK_DURATION_MINUTES = 15;

    public void saveNewOrder(ConfirmedOrderEventDTO confirmedOrderEventDTO){
        Order order = orderMapper.fromConfirmedOrderDTOtoOrder(confirmedOrderEventDTO);
        order.setLabelURI("https://example.com");
        orderRepository.save(order);
    }

    @Transactional
    public Page<OrderSummaryDTO> getPickingList(Pageable pageable, UUID orgId){
        return orderRepository.findAllSummariesByOrganizationIdAndStatuses(
                pageable,
                orgId,
                List.of(OrderWarehouseStatus.PENDING, OrderWarehouseStatus.IN_PROGRESS)
        );
    }

    @Transactional
    public OrderDTO getOrderDetails(UUID orderId, UUID orgId, UUID userId){
        Order order = orderRepository.findByIdAndOrganizationIdWithItems(orderId, orgId)
                .orElseThrow(() -> new OrderNotFoundException("No order found with id: " + orderId));

        Instant now = Instant.now();

        if (order.getLockedByUserId() != null &&
                !order.getLockedByUserId().equals(userId) &&
                order.getOrderStatus() == OrderWarehouseStatus.IN_PROGRESS &&
                order.getLockExpiryTime() != null &&
                order.getLockExpiryTime().isAfter(now)) {
            throw new OrderLockedException("Order is currently locked and being processed by another worker.");
        }

        order.setOrderStatus(OrderWarehouseStatus.IN_PROGRESS);
        order.setLockExpiryTime(now.plus(PICKING_ORDER_LOCK_DURATION_MINUTES, ChronoUnit.MINUTES));
        order.setLockedByUserId(userId);
        orderRepository.save(order);
        snsPublisherService.publishOrderStatusEvent(new OrderStatusUpdateDTO(order.getId(), OrderStatus.PROCESSING));

        return orderMapper.toOrderDTO(order);
    }

    @Transactional
    public void cancelOrderPickingLock(UUID orderId, UUID orgId) {
        Order order = orderRepository.findOrderByIdAndOrganizationId(orderId, orgId)
                .orElseThrow(() -> new OrderNotFoundException("No order found with id: " + orderId));

        order.setOrderStatus(OrderWarehouseStatus.PENDING);
        order.setLockExpiryTime(null);
        orderRepository.save(order);
    }

    public Page<ShipmentDTO> getAllOrdersReadyForShipping(Pageable pageable, UUID orgId){
        Page<Order> orders = orderRepository.findAllByOrganizationIdAndOrderStatusIn(pageable, orgId, List.of(OrderWarehouseStatus.PACKED));
        return orders.map(orderMapper::toShipmentDTO);
    }

    @Transactional
    public void markShipmentAsShipped(UUID shipmentId, UUID orgId, UUID callerUserId){
        // Verify that the caller is allowed to ship this order
        Order order = orderRepository.findOrderByIdAndOrganizationId(shipmentId, orgId)
                .orElseThrow(() -> new OrderNotFoundException(shipmentId));

        // If the order is currently locked by another worker, deny the operation
        if (order.getLockedByUserId() != null && !order.getLockedByUserId().equals(callerUserId)
                && order.getOrderStatus() == OrderWarehouseStatus.IN_PROGRESS && order.getLockExpiryTime() != null
                && order.getLockExpiryTime().isAfter(java.time.Instant.now())) {
            throw new org.springframework.security.access.AccessDeniedException("You are not the owner of this shipment.");
        }

        order.setOrderStatus(OrderWarehouseStatus.SHIPPED);
        orderRepository.save(order);
        snsPublisherService.publishOrderStatusEvent(new OrderStatusUpdateDTO(order.getId(), OrderStatus.SHIPPED));
    }


    @Transactional
    public void markOrderAsPacked(UUID orderId, UUID orgId){
        Order order = orderRepository.findOrderByIdAndOrganizationId(orderId, orgId)
                .orElseThrow(() -> new OrderNotFoundException("No order found with id: " + orderId));

        order.setOrderStatus(OrderWarehouseStatus.PACKED);
        order.setLockExpiryTime(null);
        orderRepository.save(order);
        snsPublisherService.publishOrderStatusEvent(new OrderStatusUpdateDTO(order.getId(), OrderStatus.PACKED));

    }

}
