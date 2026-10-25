package com.rostra.auction.media;

/**
 * Decides what a file really is from its first bytes. The file name and the Content-Type header come from the
 * client and are ignored, so renaming a script to photo.png does not get it stored or served as an image.
 */
public final class ImageValidator {

    public record ImageType(String contentType, String extension) {
    }

    private ImageValidator() {
    }

    /** @throws IllegalArgumentException (answered as 400) unless the bytes are a JPEG, PNG or WebP image. */
    public static ImageType detect(byte[] b) {
        if (b == null || b.length == 0) {
            throw new IllegalArgumentException("The image file is empty");
        }
        if (b.length >= 3 && u(b[0]) == 0xFF && u(b[1]) == 0xD8 && u(b[2]) == 0xFF) {
            return new ImageType("image/jpeg", "jpg");
        }
        if (b.length >= 8 && u(b[0]) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && u(b[4]) == 0x0D && u(b[5]) == 0x0A && u(b[6]) == 0x1A && u(b[7]) == 0x0A) {
            return new ImageType("image/png", "png");
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new ImageType("image/webp", "webp");
        }
        throw new IllegalArgumentException("Only JPEG, PNG or WebP images are accepted");
    }

    private static int u(byte value) {
        return value & 0xFF;
    }
}
