package com.vego.backend.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoucherOfferRepository extends JpaRepository<VoucherOffer, String> {

    List<VoucherOffer> findByActiveTrueOrderBySortOrderAsc();
}
