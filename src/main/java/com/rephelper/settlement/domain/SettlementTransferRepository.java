package com.rephelper.settlement.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SettlementTransferRepository extends JpaRepository<SettlementTransfer, UUID> {

    Optional<SettlementTransfer> findByPixTxId(String pixTxId);

    long countByCycleIdAndStatusNot(UUID cycleId, String status);
}
