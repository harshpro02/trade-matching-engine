package com.harshshah.matchingengine.repository;

import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.domain.OrderStatus;
import com.harshshah.matchingengine.domain.Side;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Order persistence, including the two book queries that define price-time priority.
 *
 * <p>Both book queries order by {@code (price, sequenceNumber)}. Price comes first because
 * price priority outranks time priority; the direction of the price sort is what makes
 * "best" mean the highest bid but the lowest ask. The sequence number breaks price ties in
 * arrival order and, unlike {@code createdAt}, is guaranteed distinct.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByAccountIdOrderBySequenceNumberDesc(UUID accountId);

    /**
     * Resting bids for a symbol, best first: highest price, then earliest arrival.
     * Read-only view, takes no locks.
     */
    @Query("""
            select o from Order o
            where o.symbol = :symbol
              and o.side = com.harshshah.matchingengine.domain.Side.BUY
              and o.status in :statuses
              and o.remainingQuantity > 0
            order by o.price desc, o.sequenceNumber asc
            """)
    List<Order> findRestingBids(@Param("symbol") String symbol,
                                @Param("statuses") Collection<OrderStatus> statuses);

    /**
     * Resting asks for a symbol, best first: lowest price, then earliest arrival.
     * Read-only view, takes no locks.
     */
    @Query("""
            select o from Order o
            where o.symbol = :symbol
              and o.side = com.harshshah.matchingengine.domain.Side.SELL
              and o.status in :statuses
              and o.remainingQuantity > 0
            order by o.price asc, o.sequenceNumber asc
            """)
    List<Order> findRestingAsks(@Param("symbol") String symbol,
                                @Param("statuses") Collection<OrderStatus> statuses);

    default List<Order> findRestingBids(String symbol) {
        return findRestingBids(symbol, OrderStatus.RESTING);
    }

    default List<Order> findRestingAsks(String symbol) {
        return findRestingAsks(symbol, OrderStatus.RESTING);
    }

    /**
     * The same two queries, but taking {@code PESSIMISTIC_WRITE} row locks, for use by the
     * matching path. Callers hold the per-symbol {@code instruments} lock already, so this
     * is defence in depth rather than the primary concurrency control: it guarantees that
     * even a code path that forgot the book lock cannot decrement a resting order that
     * another transaction is mid-way through filling.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from Order o
            where o.symbol = :symbol
              and o.side = :side
              and o.status in :statuses
              and o.remainingQuantity > 0
            order by
              case when :side = com.harshshah.matchingengine.domain.Side.BUY
                   then -o.price else o.price end asc,
              o.sequenceNumber asc
            """)
    List<Order> lockRestingOrders(@Param("symbol") String symbol,
                                  @Param("side") Side side,
                                  @Param("statuses") Collection<OrderStatus> statuses);

    default List<Order> lockRestingOrders(String symbol, Side side) {
        return lockRestingOrders(symbol, side, OrderStatus.RESTING);
    }
}
