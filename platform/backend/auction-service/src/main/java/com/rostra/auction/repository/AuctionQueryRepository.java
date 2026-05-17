package com.rostra.auction.repository;

import com.rostra.auction.entity.Auction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuctionQueryRepository {
    Page<Auction> search(AuctionSearchCriteria criteria, Pageable pageable);
}
