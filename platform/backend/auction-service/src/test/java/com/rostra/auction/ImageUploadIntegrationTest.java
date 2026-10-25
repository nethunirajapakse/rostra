package com.rostra.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rostra.auction.support.AuctionFixtures;
import com.rostra.auction.support.InMemoryImageStorage;
import com.rostra.auction.support.PostgresIntegrationTest;
import com.rostra.auction.support.TestTokens;
import com.rostra.auction.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ImageUploadIntegrationTest.InMemoryStorageConfig.class)
class ImageUploadIntegrationTest extends PostgresIntegrationTest {

    @TestConfiguration
    static class InMemoryStorageConfig {
        @Bean
        @Primary
        InMemoryImageStorage inMemoryImageStorage() {
            return new InMemoryImageStorage();
        }
    }

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ImageStorage storage;

    private final UUID seller = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private InMemoryImageStorage memory() {
        return (InMemoryImageStorage) storage;
    }

    private UUID auction() throws Exception {
        return AuctionFixtures.createAuction(mvc, objectMapper, seller, "With photos");
    }

    private MvcResult upload(UUID auctionId, UUID user, byte[] bytes, String filename, String contentType) throws Exception {
        return mvc.perform(multipart("/auctions/{id}/images", auctionId)
                        .file(new MockMultipartFile("file", filename, contentType, bytes))
                        .cookie(TestTokens.accessCookie(user)))
                .andReturn();
    }

    private JsonNode uploadOk(UUID auctionId) throws Exception {
        MvcResult result = upload(auctionId, seller, PNG, "a.png", "image/png");
        assertEquals(201, result.getResponse().getStatus());
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void sellerUploadsAnImageAndItAppearsOnTheAuction() throws Exception {
        UUID id = auction();

        JsonNode image = uploadOk(id);
        assertEquals("/auctions/" + id + "/images/" + image.get("id").asText() + "/content", image.get("url").asText());
        assertEquals(0, image.get("position").asInt());

        String prefix = "auctions/" + id + "/";
        assertEquals(1, memory().countUnder(prefix));
        assertEquals("image/png", memory().contentTypeUnder(prefix));

        mvc.perform(get("/auctions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl").value(image.get("url").asText()))
                .andExpect(jsonPath("$.images", hasSize(1)))
                .andExpect(jsonPath("$.images[0].id").value(image.get("id").asText()));
    }

    @Test
    void thePhotoCanBeReadByAnyoneAndIsCachedForAYear() throws Exception {
        UUID id = auction();
        JsonNode image = uploadOk(id);

        byte[] served = mvc.perform(get(image.get("url").asText())) // no cookie: auctions are public
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(PNG, served);

        mvc.perform(get("/auctions/{id}/images/{imageId}/content", id, UUID.randomUUID()))
                .andExpect(status().isNotFound());
        // An image id from another auction must not resolve under this one.
        UUID other = auction();
        mvc.perform(get("/auctions/{id}/images/{imageId}/content", other, image.get("id").asText()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listResponsesIncludeTheCover() throws Exception {
        UUID id = auction();
        JsonNode image = uploadOk(id);

        mvc.perform(get("/auctions").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + id + "')].coverUrl").value(image.get("url").asText()));
    }

    @Test
    void anAuctionWithoutImagesHasNoCover() throws Exception {
        UUID id = auction();
        mvc.perform(get("/auctions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl").doesNotExist())
                .andExpect(jsonPath("$.images", hasSize(0)));
    }

    @Test
    void aFileThatIsNotAnImageIsRejectedEvenIfItIsCalledPng() throws Exception {
        UUID id = auction();
        MvcResult result = upload(id, seller, "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8), "evil.png", "image/png");

        assertEquals(400, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("JPEG, PNG or WebP"));
        mvc.perform(get("/auctions/{id}", id)).andExpect(jsonPath("$.images", hasSize(0)));
    }

    @Test
    void onlyTheSellerCanUpload() throws Exception {
        UUID id = auction();
        assertEquals(403, upload(id, stranger, PNG, "a.png", "image/png").getResponse().getStatus());
    }

    @Test
    void anonymousUploadIs401() throws Exception {
        UUID id = auction();
        mvc.perform(multipart("/auctions/{id}/images", id)
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadingToAnUnknownAuctionIs404() throws Exception {
        assertEquals(404, upload(UUID.randomUUID(), seller, PNG, "a.png", "image/png").getResponse().getStatus());
    }

    @Test
    void anAuctionHoldsAtMostFiveImages() throws Exception {
        UUID id = auction();
        for (int i = 0; i < 5; i++) {
            uploadOk(id);
        }
        MvcResult sixth = upload(id, seller, PNG, "a.png", "image/png");
        assertEquals(409, sixth.getResponse().getStatus());
        assertTrue(sixth.getResponse().getContentAsString().contains("at most 5"));
    }

    @Test
    void cancelledAuctionsCannotChangeTheirImages() throws Exception {
        UUID id = auction();
        mvc.perform(delete("/auctions/{id}", id).cookie(TestTokens.accessCookie(seller))).andExpect(status().isOk());
        assertEquals(409, upload(id, seller, PNG, "a.png", "image/png").getResponse().getStatus());
    }

    @Test
    void deletingAnImageRemovesTheRowAndTheStoredObject() throws Exception {
        UUID id = auction();
        JsonNode image = uploadOk(id);
        String prefix = "auctions/" + id + "/";

        mvc.perform(delete("/auctions/{id}/images/{imageId}", id, image.get("id").asText())
                        .cookie(TestTokens.accessCookie(stranger)))
                .andExpect(status().isForbidden());
        assertEquals(1, memory().countUnder(prefix));

        mvc.perform(delete("/auctions/{id}/images/{imageId}", id, image.get("id").asText())
                        .cookie(TestTokens.accessCookie(seller)))
                .andExpect(status().isNoContent());
        assertEquals(0, memory().countUnder(prefix));

        mvc.perform(get("/auctions/{id}", id)).andExpect(jsonPath("$.images", hasSize(0)));
        mvc.perform(delete("/auctions/{id}/images/{imageId}", id, image.get("id").asText())
                        .cookie(TestTokens.accessCookie(seller)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reorderingChangesTheCover() throws Exception {
        UUID id = auction();
        JsonNode first = uploadOk(id);
        JsonNode second = uploadOk(id);
        JsonNode third = uploadOk(id);

        String body = objectMapper.writeValueAsString(
                java.util.Map.of("imageIds", List.of(third.get("id").asText(), first.get("id").asText(), second.get("id").asText())));

        mvc.perform(put("/auctions/{id}/images/order", id)
                        .cookie(TestTokens.accessCookie(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(third.get("id").asText()));

        mvc.perform(get("/auctions/{id}", id))
                .andExpect(jsonPath("$.coverUrl").value(third.get("url").asText()))
                .andExpect(jsonPath("$.images[1].id").value(first.get("id").asText()))
                .andExpect(jsonPath("$.images[2].id").value(second.get("id").asText()));
    }

    @Test
    void reorderMustNameEveryImageExactlyOnce() throws Exception {
        UUID id = auction();
        JsonNode first = uploadOk(id);
        uploadOk(id);

        String body = objectMapper.writeValueAsString(java.util.Map.of("imageIds", List.of(first.get("id").asText())));
        mvc.perform(put("/auctions/{id}/images/order", id)
                        .cookie(TestTokens.accessCookie(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("every image")));
    }
}
