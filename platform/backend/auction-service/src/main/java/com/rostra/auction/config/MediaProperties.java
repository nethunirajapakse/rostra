package com.rostra.auction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Image storage settings (app.media.*). Any S3-compatible store works: RustFS, SeaweedFS, Garage, AWS S3. */
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
        String endpoint,
        String bucket,
        String accessKey,
        String secretKey,
        String region,
        long maxFileBytes,
        int maxImagesPerAuction
) {
}
