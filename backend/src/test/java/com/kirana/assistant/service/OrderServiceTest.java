package com.kirana.assistant.service;

import com.kirana.assistant.dto.CreateOrderRequest;
import com.kirana.assistant.dto.OrderItemRequest;
import com.kirana.assistant.exception.InvalidOrderException;
import com.kirana.assistant.exception.InvalidStatusTransitionException;
import com.kirana.assistant.exception.OrderNotFoundException;
import com.kirana.assistant.model.Order;
import com.kirana.assistant.model.OrderStatus;
import com.kirana.assistant.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for order creation / retrieval / status rules.
 * No MongoDB, no Mockito — hand-rolled fakes so tests run on any JDK.
 */
class OrderServiceTest {

    private InMemoryOrderRepository repo;
    private RecordingNotifier notifier;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        repo = new InMemoryOrderRepository();
        notifier = new RecordingNotifier();
        orderService = new OrderService(repo, notifier);
    }

    private CreateOrderRequest validRequest() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerName("Rahul");
        req.setCustomerPhone("+919999999999");
        OrderItemRequest milk = new OrderItemRequest();
        milk.setName("Amul Milk");
        milk.setQuantity(2);
        milk.setUnit("packet");
        OrderItemRequest maggi = new OrderItemRequest();
        maggi.setName("Maggi");
        maggi.setQuantity(3);
        maggi.setUnit("packet");
        req.setItems(List.of(milk, maggi));
        req.setPickupTime("19:30");
        return req;
    }

    @Test
    void createOrderPersistsPendingOrder() {
        Order saved = orderService.create(validRequest());

        assertEquals("Rahul", saved.getCustomerName());
        assertEquals(OrderStatus.PENDING, saved.getStatus());
        assertEquals(2, saved.getItems().size());
        assertEquals("19:30", saved.getPickupTime());
        assertEquals(List.of("CREATED"), notifier.actions);
    }

    @Test
    void createOrderRejectsEmptyItems() {
        CreateOrderRequest req = validRequest();
        req.setItems(List.of());
        assertThrows(InvalidOrderException.class, () -> orderService.create(req));
    }

    @Test
    void createOrderRejectsBlankCustomer() {
        CreateOrderRequest req = validRequest();
        req.setCustomerName("  ");
        assertThrows(InvalidOrderException.class, () -> orderService.create(req));
    }

    @Test
    void createOrderRejectsInvalidQuantity() {
        CreateOrderRequest req = validRequest();
        req.getItems().get(0).setQuantity(0);
        assertThrows(InvalidOrderException.class, () -> orderService.create(req));
    }

    @Test
    void getByIdThrowsWhenMissing() {
        assertThrows(OrderNotFoundException.class, () -> orderService.getById("nope"));
    }

    @Test
    void getByIdRejectsBlankId() {
        assertThrows(InvalidOrderException.class, () -> orderService.getById(" "));
    }

    @Test
    void statusFlowPendingToCompleted() {
        Order order = orderService.create(validRequest());
        String id = order.getId();

        assertEquals(OrderStatus.ACCEPTED, orderService.updateStatus(id, "ACCEPTED").getStatus());
        assertEquals(OrderStatus.PREPARING, orderService.updateStatus(id, "PREPARING").getStatus());
        assertEquals(OrderStatus.READY, orderService.updateStatus(id, "READY").getStatus());
        assertEquals(OrderStatus.COMPLETED, orderService.updateStatus(id, "COMPLETED").getStatus());
    }

    @Test
    void statusFlowRejectsCompletedToPending() {
        Order order = orderService.create(validRequest());
        orderService.updateStatus(order.getId(), "ACCEPTED");
        orderService.updateStatus(order.getId(), "PREPARING");
        orderService.updateStatus(order.getId(), "READY");
        orderService.updateStatus(order.getId(), "COMPLETED");
        assertThrows(InvalidStatusTransitionException.class,
                () -> orderService.updateStatus(order.getId(), "PENDING"));
    }

    @Test
    void statusFlowRejectsUnknownStatus() {
        Order order = orderService.create(validRequest());
        assertThrows(InvalidOrderException.class,
                () -> orderService.updateStatus(order.getId(), "FLYING"));
    }

    @Test
    void statusFlowPendingCanBeRejected() {
        Order order = orderService.create(validRequest());
        assertEquals(OrderStatus.REJECTED,
                orderService.updateStatus(order.getId(), "REJECTED").getStatus());
    }

    @Test
    void deleteRemovesAndNotifies() {
        Order order = orderService.create(validRequest());
        orderService.delete(order.getId());
        assertTrue(repo.store.isEmpty());
        assertTrue(notifier.actions.contains("DELETED"));
    }

    // ---------- fakes ----------

    /** No-op notifier that records actions (avoids Mockito on new JDKs). */
    static class RecordingNotifier extends DashboardNotifierService {
        final List<String> actions = new ArrayList<>();

        public RecordingNotifier() {
            super(null, null);
        }

        @Override
        public void notifyOrderUpdate(Order order, String action) {
            actions.add(action);
        }

        @Override
        public void notifyCallUpdate(String message) {
        }
    }

    /** Minimal in-memory OrderRepository. */
    static class InMemoryOrderRepository implements OrderRepository {
        final Map<String, Order> store = new HashMap<>();

        @Override
        public <S extends Order> S save(S entity) {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID().toString());
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public Optional<Order> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public void delete(Order entity) {
            store.remove(entity.getId());
        }

        @Override
        public List<Order> findAllByOrderByCreatedAtDesc() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status) {
            List<Order> out = new ArrayList<>();
            for (Order o : store.values()) {
                if (status.equals(o.getStatus())) {
                    out.add(o);
                }
            }
            return out;
        }

        @Override
        public List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId) {
            return List.of();
        }

        @Override
        public Optional<Order> findFirstByCustomerIdOrderByCreatedAtDesc(String customerId) {
            return Optional.empty();
        }

        // --- unused interface methods ---
        @Override
        public <S extends Order> List<S> saveAll(Iterable<S> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Order> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<Order> findAll(Sort sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Order> findAll(Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Order> findAllById(Iterable<String> ids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void deleteById(String id) {
            store.remove(id);
        }

        @Override
        public void deleteAllById(Iterable<? extends String> ids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAll(Iterable<? extends Order> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public boolean existsById(String id) {
            return store.containsKey(id);
        }

        @Override
        public <S extends Order> S insert(S entity) {
            return save(entity);
        }

        @Override
        public <S extends Order> List<S> insert(Iterable<S> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Order> Optional<S> findOne(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Order> List<S> findAll(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Order> List<S> findAll(Example<S> example, Sort sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Order> Page<S> findAll(Example<S> example, Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Order> long count(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Order> boolean exists(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Order, R> R findBy(Example<S> example,
                                             Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            throw new UnsupportedOperationException();
        }
    }
}
