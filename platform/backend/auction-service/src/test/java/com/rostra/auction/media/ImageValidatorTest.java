package com.rostra.auction.media;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageValidatorTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Test
    void recognisesPng() {
        assertEquals("image/png", ImageValidator.detect(PNG).contentType());
    }

    @Test
    void recognisesJpeg() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
        assertEquals("image/jpeg", ImageValidator.detect(jpeg).contentType());
        assertEquals("jpg", ImageValidator.detect(jpeg).extension());
    }

    @Test
    void recognisesWebp() {
        byte[] webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("image/webp", ImageValidator.detect(webp).contentType());
    }

    @Test
    void rejectsEverythingElse() {
        assertThrows(IllegalArgumentException.class, () -> ImageValidator.detect(new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> ImageValidator.detect("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
                () -> ImageValidator.detect("GIF89a....".getBytes(StandardCharsets.UTF_8)));
        // A RIFF container that is not WebP (for example WAV audio).
        assertThrows(IllegalArgumentException.class,
                () -> ImageValidator.detect("RIFF\u0000\u0000\u0000\u0000WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1)));
    }
}
