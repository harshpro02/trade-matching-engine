package com.harshshah.matchingengine.repository;

import com.harshshah.matchingengine.domain.Order;
import com.harshshah.matchingengine.domain.OrderStatus;
import com.harshshah.matchingengine.domain.Side;
import com.harshshah.matchingengine.dto.PriceLevelResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByAccountIdOrderBySequenceNumberDesc(UUID accountId, Pageable pageable);

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

    @Query("""
            select new com.harshshah.matchingengine.dto.PriceLevelResponse(
                       o.price, sum(o.remainingQuantity), count(o))
            from Order o
            where o.symbol = :symbol
              and o.side = com.harshshah.matchingengine.domain.Side.BUY
              and o.status in :statuses
              and o.remainingQuantity > 0
            group by o.price
            order by o.price desc
            """)
    List<PriceLevelResponse> aggregateBids(@Param("symbol") String symbol,
                                           @Param("statuses") Collection<OrderStatus> statuses,
                                           Pageable depth);

    @Query("""
            select new com.harshshah.matchingengine.dto.PriceLevelResponse(
                       o.price, sum(o.remainingQuantity), count(o))
            from Order o
            where o.symbol = :symbol
              and o.side = com.harshshah.matchingengine.domain.Side.SELL
              and o.status in :statuses
              and o.remainingQuantity > 0
            group by o.price
            order by o.price asc
            """)
    List<PriceLevelResponse> aggregateAsks(@Param("symbol") String symbol,
                                           @Param("statuses") Collection<OrderStatus> statuses,
                                           Pageable depth);

    default List<PriceLevelResponse> aggregateBids(String symbol, Pageable depth) {
        return aggregateBids(symbol, OrderStatus.RESTING, depth);
    }

    default List<PriceLevelResponse> aggregateAsks(String symbol, Pageable depth) {
        return aggregateAsks(symbol, OrderStatus.RESTING, depth);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from Order o
            where o.symbol = :symbol
              and o.side = com.harshshah.matchingengine.domain.Side.BUY
              and o.status in :statuses
              and o.remainingQuantity > 0
            order by o.price desc, o.sequenceNumber asc
            """)
    List<Order> lockRestingBids(@Param("symbol") String symbol,
                                @Param("statuses") Collection<OrderStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from Order o
            where o.symbol = :symbol
              and o.side = com.harshshah.matchingengine.domain.Side.SELL
              and o.status in :statuses
              and o.remainingQuantity > 0
            order by o.price asc, o.sequenceNumber asc
            """)
    List<Order> lockRestingAsks(@Param("symbol") String symbol,
                                @Param("statuses") Collection<OrderStatus> statuses);

    default List<Order> lockRestingOrders(String symbol, Side side) {
        return side == Side.BUY
                ? lockRestingBids(symbol, OrderStatus.RESTING)
                : lockRestingAsks(symbol, OrderStatus.RESTING);
    }
}
