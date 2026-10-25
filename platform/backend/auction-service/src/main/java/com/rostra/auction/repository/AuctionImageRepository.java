package com.rostra.auction.repository;

import com.rostra.auction.entity.AuctionImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuctionImageRepository extends JpaRepository<AuctionImage, UUID> {

    List<AuctionImage> findByAuctionIdOrderBySortOrderAscCreatedAtAsc(UUID auctionId);

    /** One query for a whole page of auctions, so listing does not cost a query per auction. */
    List<AuctionImage> findByAuctionIdInOrderBySortOrderAscCreatedAtAsc(Collection<UUID> auctionIds);

    long countByAuctionId(UUID auctionId);

    Optional<AuctionImage> findByIdAndAuctionId(UUID id, UUID auctionId);

    @Query("select coalesce(max(i.sortOrder), -1) + 1 from AuctionImage i where i.auctionId = :auctionId")
    Integer nextSortOrder(@Param("auctionId") UUID auctionId);
}
