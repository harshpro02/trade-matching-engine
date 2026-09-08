package com.harshshah.matchingengine.repository;

import com.harshshah.matchingengine.domain.Instrument;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {

    /**
     * Take an exclusive row lock on one symbol's book. Every order submission and every
     * cancel for that symbol goes through here first, which serialises them against each
     * other while leaving other symbols entirely unblocked.
     *
     * <p>Emits {@code SELECT ... FOR UPDATE}. The caller must already be in a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Instrument i where i.symbol = :symbol")
    Optional<Instrument> findAndLockBySymbol(@Param("symbol") String symbol);
}
