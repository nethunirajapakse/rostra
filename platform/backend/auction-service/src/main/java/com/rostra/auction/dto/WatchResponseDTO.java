package com.rostra.auction.dto;

import java.util.UUID;

/** Answer to watch / unwatch: the auction concerned and whether the caller is watching it afterwards. */
public record WatchResponseDTO(UUID auctionId, boolean watching) {}
