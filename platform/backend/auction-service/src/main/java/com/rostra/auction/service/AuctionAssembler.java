package com.rostra.auction.service;

import com.rostra.auction.dto.AuctionImageDTO;
import com.rostra.auction.dto.AuctionResponseDTO;
import com.rostra.auction.entity.Auction;
import com.rostra.auction.entity.AuctionImage;
import com.rostra.auction.repository.AuctionImageRepository;
import com.rostra.auction.repository.WatchlistRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;

/** Turns auctions into API responses and attaches their images with a single extra query per page. */
@Component
public class AuctionAssembler {

    private final AuctionImageRepository imageRepository;
    private final WatchlistRepository watchlistRepository;

    public AuctionAssembler(AuctionImageRepository imageRepository,
                            WatchlistRepository watchlistRepository) {
        this.imageRepository = imageRepository;
        this.watchlistRepository = watchlistRepository;
    }

    /** For callers with no user in the picture (internal calls): {@code watching} is false. */
    public AuctionResponseDTO toDto(Auction auction) {
        return toDto(auction, null);
    }

    /** @param viewerId the signed-in user, or null for an anonymous caller */
    public AuctionResponseDTO toDto(Auction auction, UUID viewerId) {
        return toDtos(List.of(auction), viewerId).get(0);
    }

    public Page<AuctionResponseDTO> toPage(Page<Auction> page, UUID viewerId) {
        return new PageImpl<>(toDtos(page.getContent(), viewerId), page.getPageable(), page.getTotalElements());
    }

    public List<AuctionResponseDTO> toDtos(List<Auction> auctions, UUID viewerId) {
        if (auctions.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = auctions.stream().map(Auction::getId).toList();
        Map<UUID, List<AuctionImageDTO>> imagesByAuction = new HashMap<>();
        for (AuctionImage image : imageRepository.findByAuctionIdInOrderBySortOrderAscCreatedAtAsc(ids)) {
            imagesByAuction.computeIfAbsent(image.getAuctionId(), k -> new ArrayList<>()).add(toImageDto(image));
        }
        Set<UUID> watched = viewerId == null
                ? Set.of()
                : new HashSet<>(watchlistRepository.findWatchedIds(viewerId, ids));
        return auctions.stream()
                .map(a -> AuctionResponseDTO.from(a,
                        imagesByAuction.getOrDefault(a.getId(), List.of()),
                        watched.contains(a.getId())))
                .toList();
    }

    public AuctionImageDTO toImageDto(AuctionImage image) {
        // Photos are read back through this service, so the URL is an ordinary API path on the web app's origin.
        String url = "/auctions/" + image.getAuctionId() + "/images/" + image.getId() + "/content";
        return new AuctionImageDTO(image.getId(), url, image.getSortOrder());
    }
}
