package com.rostra.auction.controller;

import com.rostra.auction.dto.AuctionImageDTO;
import com.rostra.auction.dto.ReorderImagesRequestDTO;
import com.rostra.auction.service.AuctionAssembler;
import com.rostra.auction.service.AuctionImageService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Photos of an auction. Reading them needs nothing extra: they come with the auction itself (images, coverUrl)
 * and the files come from the /content endpoint below. Changing them is for the seller only.
 */
@RestController
@RequestMapping("/auctions/{auctionId}/images")
public class AuctionImageController {

    private final AuctionImageService imageService;
    private final AuctionAssembler assembler;

    public AuctionImageController(AuctionImageService imageService, AuctionAssembler assembler) {
        this.imageService = imageService;
        this.assembler = assembler;
    }

    /**
     * The photo itself. Public, like the auction. Its URL never changes content (a replaced photo gets a new id),
     * so browsers may keep it for a year.
     */
    @GetMapping("/{imageId}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID auctionId, @PathVariable UUID imageId) {
        AuctionImageService.ImageContent image = imageService.read(auctionId, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.data());
    }

    /** One file per request (multipart field "file"). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuctionImageDTO> upload(
            @PathVariable UUID auctionId,
            @AuthenticationPrincipal UUID userId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        AuctionImageDTO body = assembler.toImageDto(imageService.add(userId, auctionId, file.getBytes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID auctionId,
            @PathVariable UUID imageId,
            @AuthenticationPrincipal UUID userId
    ) {
        imageService.delete(userId, auctionId, imageId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    public List<AuctionImageDTO> reorder(
            @PathVariable UUID auctionId,
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ReorderImagesRequestDTO req
    ) {
        return imageService.reorder(userId, auctionId, req.imageIds()).stream()
                .map(assembler::toImageDto)
                .toList();
    }
}
