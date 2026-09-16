package com.harshshah.matchingengine.repository;

import com.harshshah.matchingengine.domain.Instrument;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Instrument i where i.symbol = :symbol")
    Optional<Instrument> findAndLockBySymbol(@Param("symbol") String symbol);
}
