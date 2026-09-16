package com.harshshah.matchingengine.repository;

import com.harshshah.matchingengine.domain.Position;
import com.harshshah.matchingengine.domain.PositionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, PositionId> {
    List<Position> findByAccountId(UUID accountId);

    Optional<Position> findByAccountIdAndSymbol(UUID accountId, String symbol);
}
