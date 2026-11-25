package com.rephelper.settlement.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SettlementCycleRepository extends JpaRepository<SettlementCycle, UUID> {

    @EntityGraph(attributePaths = "transfers")
    Optional<SettlementCycle> findWithTransfersById(UUID id);

    boolean existsByRepublicIdAndReferenceMonth(UUID republicId, String referenceMonth);
}
