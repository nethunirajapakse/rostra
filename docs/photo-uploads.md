# Photo uploads

Photos go to an S3-compatible bucket (RustFS locally, any S3 service in production). The bucket stays private: auction-service streams each image itself from `/auctions/{id}/images/{imageId}/content`, with long-lived cache headers and `X-Content-Type-Options: nosniff`.

## Rules

- JPEG, PNG or WebP only, up to 5 MB each, up to 5 photos per auction.
- The file type is read from the file's magic bytes, not the filename or `Content-Type`, so a renamed file is rejected.
- Only the seller can change photos, and only while the auction is draft, scheduled or active.

## Design choices

- **The 5-photo limit holds under concurrency.** Uploads take a row lock on the auction (`findByIdForUpdate`), so simultaneous requests cannot exceed the cap.
- **No orphaned files.** Storage cleanup runs after the database transaction completes, so a rollback does not leave files behind.
- **No N+1.** Auction lists load photos with one query and the viewer's watched ids with another.
- **AWS SDK v2 is pinned to 2.25.60** on purpose. Newer versions send default checksum headers that some S3-compatible stores reject.
- **RustFS, not MinIO**, because MinIO's free Docker Hub images were withdrawn.

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/auctions/{id}/images` | Upload one photo (multipart) |
| DELETE | `/auctions/{id}/images/{imageId}` | Delete a photo |
| PUT | `/auctions/{id}/images/order` | Reorder; the first is the cover |
| GET | `/auctions/{id}/images/{imageId}/content` | The image itself |
