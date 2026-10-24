package com.rostra.auction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rostra.auction.support.AuctionFixtures;
import com.rostra.auction.support.PostgresIntegrationTest;
import com.rostra.auction.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Create, fetch, update and cancel an auction through the real stack: security filters, controller, service,
 * JPA and a fresh Postgres container. Requests carry a real signed access-token cookie.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuctionCrudFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID seller = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @Test
    void createFetchUpdateCancelFlow() throws Exception {
        // Whole seconds, so the value read back from Postgres is exactly what was sent.
        Instant startsAt = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);
        String createBody = """
                {"title":"Vintage camera","description":"Works, with the original strap",
                 "startingPrice":100.00,"minIncrement":10.00,"startsAt":"%s","endsAt":"%s"}
                """.formatted(startsAt, endsAt);

        // 1. create
        MvcResult created = mvc.perform(post("/auctions")
                        .cookie(TestTokens.accessCookie(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/auctions/")))
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        // 2. fetch it back (public endpoint, so no cookie) and check every field
        mvc.perform(get("/auctions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.sellerId").value(seller.toString()))
                .andExpect(jsonPath("$.title").value("Vintage camera"))
                .andExpect(jsonPath("$.description").value("Works, with the original strap"))
                .andExpect(jsonPath("$.startingPrice").value(100.0))
                .andExpect(jsonPath("$.currentPrice").value(100.0))
                .andExpect(jsonPath("$.minIncrement").value(10.0))
                .andExpect(jsonPath("$.startsAt").value(startsAt.toString()))
                .andExpect(jsonPath("$.endsAt").value(endsAt.toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.winnerId").doesNotExist())
                .andExpect(jsonPath("$.version").value(0));

        // 3. patch the title: the change is returned, persisted, and the version moves from 0 to 1
        mvc.perform(patch("/auctions/{id}", id)
                        .cookie(TestTokens.accessCookie(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Vintage camera (restored)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Vintage camera (restored)"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/auctions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Vintage camera (restored)"))
                .andExpect(jsonPath("$.description").value("Works, with the original strap"))
                .andExpect(jsonPath("$.version").value(1));

        // 4. cancel
        mvc.perform(delete("/auctions/{id}", id).cookie(TestTokens.accessCookie(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/auctions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 5. a cancelled auction can no longer be edited
        mvc.perform(patch("/auctions/{id}", id)
                        .cookie(TestTokens.accessCookie(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Too late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("CANCELLED")));
    }

    @Test
    void onlyTheSellerCanEdit() throws Exception {
        UUID id = AuctionFixtures.createAuction(mvc, objectMapper, seller, "Mine");

        mvc.perform(patch("/auctions/{id}", id)
                        .cookie(TestTokens.accessCookie(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/auctions/{id}", id))
                .andExpect(jsonPath("$.title").value("Mine"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void anonymousVisitorsCannotCreate() throws Exception {
        mvc.perform(post("/auctions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownAuctionIsNotFound() throws Exception {
        mvc.perform(get("/auctions/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void endBeforeStartIsRejected() throws Exception {
        Instant startsAt = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.minus(1, ChronoUnit.HOURS);
        String body = """
                {"title":"Backwards","description":"x","startingPrice":10.00,"minIncrement":1.00,
                 "startsAt":"%s","endsAt":"%s"}
                """.formatted(startsAt, endsAt);

        mvc.perform(post("/auctions")
                        .cookie(TestTokens.accessCookie(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
