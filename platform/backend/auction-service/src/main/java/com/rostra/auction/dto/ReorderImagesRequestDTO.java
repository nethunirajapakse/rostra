package com.rostra.auction.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Every image of the auction, in the new order. The first one becomes the cover. */
public record ReorderImagesRequestDTO(@NotEmpty List<UUID> imageIds) {
}
