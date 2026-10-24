package com.rostra.auction.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class AuctionFixtures {

    private AuctionFixtures() {
    }

    /** Creates a SCHEDULED auction (it starts in an hour) through the real endpoint and returns its id. */
    public static UUID createAuction(MockMvc mvc, ObjectMapper mapper, UUID sellerId, String title) throws Exception {
        Instant startsAt = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant endsAt = startsAt.plus(2, ChronoUnit.HOURS);
        String body = """
                {"title":"%s","description":"fixture","startingPrice":50.00,"minIncrement":5.00,
                 "startsAt":"%s","endsAt":"%s"}
                """.formatted(title, startsAt, endsAt);

        String response = mvc.perform(post("/auctions")
                        .cookie(TestTokens.accessCookie(sellerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(mapper.readTree(response).get("id").asText());
    }
}
