package com.rostra.auction.service;

import com.rostra.auction.config.MediaProperties;
import com.rostra.auction.entity.Auction;
import com.rostra.auction.entity.AuctionImage;
import com.rostra.auction.entity.AuctionStatus;
import com.rostra.auction.exception.AuctionNotFoundException;
import com.rostra.auction.exception.ForbiddenException;
import com.rostra.auction.exception.IllegalAuctionStateException;
import com.rostra.auction.exception.ImageNotFoundException;
import com.rostra.auction.media.ImageValidator;
import com.rostra.auction.repository.AuctionImageRepository;
import com.rostra.auction.repository.AuctionRepository;
import com.rostra.auction.storage.ImageStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AuctionImageServiceImpl implements AuctionImageService {

    /** Photos can change until the auction is over. */
    private static final Set<AuctionStatus> OPEN_STATUSES =
            Set.of(AuctionStatus.DRAFT, AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE);

    private final AuctionRepository auctionRepository;
    private final AuctionImageRepository imageRepository;
    private final ImageStorage storage;
    private final MediaProperties props;

    public AuctionImageServiceImpl(AuctionRepository auctionRepository,
                                   AuctionImageRepository imageRepository,
                                   ImageStorage storage,
                                   MediaProperties props) {
        this.auctionRepository = auctionRepository;
        this.imageRepository = imageRepository;
        this.storage = storage;
        this.props = props;
    }

    @Override
    @Transactional(readOnly = true)
    public ImageContent read(UUID auctionId, UUID imageId) {
        AuctionImage image = imageRepository.findByIdAndAuctionId(imageId, auctionId)
                .orElseThrow(() -> new ImageNotFoundException(imageId));
        byte[] data = storage.get(image.getObjectKey());
        if (data == null) {
            throw new ImageNotFoundException(imageId);
        }
        return new ImageContent(data, image.getContentType());
    }

    @Override
    @Transactional
    public AuctionImage add(UUID userId, UUID auctionId, byte[] data) {
        lockOwnedOpenAuction(userId, auctionId);

        if (data.length > props.maxFileBytes()) {
            throw new IllegalArgumentException("Image is too large (limit " + props.maxFileBytes() / (1024 * 1024) + " MB)");
        }
        ImageValidator.ImageType type = ImageValidator.detect(data);

        if (imageRepository.countByAuctionId(auctionId) >= props.maxImagesPerAuction()) {
            throw new IllegalAuctionStateException(
                    "An auction can have at most " + props.maxImagesPerAuction() + " images");
        }

        String key = "auctions/" + auctionId + "/" + UUID.randomUUID() + "." + type.extension();
        storage.put(key, data, type.contentType());
        // If anything below fails or the transaction rolls back, do not leave the uploaded object behind.
        afterCompletion(status -> {
            if (status != TransactionSynchronization.STATUS_COMMITTED) {
                storage.delete(key);
            }
        });

        int sortOrder = imageRepository.nextSortOrder(auctionId);
        return imageRepository.save(new AuctionImage(auctionId, key, type.contentType(), data.length, sortOrder));
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID auctionId, UUID imageId) {
        lockOwnedOpenAuction(userId, auctionId);
        AuctionImage image = imageRepository.findByIdAndAuctionId(imageId, auctionId)
                .orElseThrow(() -> new ImageNotFoundException(imageId));

        imageRepository.delete(image);
        String key = image.getObjectKey();
        afterCompletion(status -> {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                storage.delete(key);
            }
        });
    }

    @Override
    @Transactional
    public List<AuctionImage> reorder(UUID userId, UUID auctionId, List<UUID> orderedIds) {
        lockOwnedOpenAuction(userId, auctionId);
        List<AuctionImage> current = imageRepository.findByAuctionIdOrderBySortOrderAscCreatedAtAsc(auctionId);

        Set<UUID> requested = new HashSet<>(orderedIds);
        Set<UUID> existing = current.stream().map(AuctionImage::getId).collect(Collectors.toSet());
        if (requested.size() != orderedIds.size() || !requested.equals(existing)) {
            throw new IllegalArgumentException("imageIds must list every image of this auction exactly once");
        }

        Map<UUID, AuctionImage> byId = current.stream().collect(Collectors.toMap(AuctionImage::getId, Function.identity()));
        List<AuctionImage> result = new ArrayList<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            AuctionImage image = byId.get(orderedIds.get(i));
            image.setSortOrder(i);
            result.add(image);
        }
        return result;
    }

    private Auction lockOwnedOpenAuction(UUID userId, UUID auctionId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));
        if (!auction.getSellerId().equals(userId)) {
            throw new ForbiddenException("Only the seller can change this auction's images");
        }
        if (!OPEN_STATUSES.contains(auction.getStatus())) {
            throw new IllegalAuctionStateException("Cannot change images of an auction in status: " + auction.getStatus());
        }
        return auction;
    }

    private static void afterCompletion(java.util.function.IntConsumer action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                action.accept(status);
            }
        });
    }
}
