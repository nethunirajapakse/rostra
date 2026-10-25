package com.rostra.auction.storage;

import com.rostra.auction.config.MediaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Plain S3 calls only (create bucket, put, get, delete), so it works with any S3-compatible store. The bucket
 * stays private: images are read back through this service, not straight from storage.
 */
@Component
public class S3ImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ImageStorage.class);

    private final S3Client s3;
    private final MediaProperties props;
    private volatile boolean bucketReady;

    public S3ImageStorage(S3Client s3, MediaProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        ensureBucket();
        s3.putObject(PutObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
    }

    @Override
    public byte[] get(String key) {
        try {
            return s3.getObjectAsBytes(b -> b.bucket(props.bucket()).key(key)).asByteArray();
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(b -> b.bucket(props.bucket()).key(key));
        } catch (RuntimeException e) {
            log.warn("Could not delete image object {}: {}", key, e.toString());
        }
    }

    /**
     * Creates the bucket on first use rather than at start-up, so the service does not depend on the object
     * store being ready when it boots.
     */
    private synchronized void ensureBucket() {
        if (bucketReady) {
            return;
        }
        String bucket = props.bucket();
        try {
            s3.headBucket(b -> b.bucket(bucket));
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            s3.createBucket(b -> b.bucket(bucket));
            log.info("Created image bucket '{}'", bucket);
        }
        bucketReady = true;
    }
}
