package com.rostra.auction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rostra.auction.support.AuctionFixtures;
import com.rostra.auction.support.PostgresIntegrationTest;
import com.rostra.auction.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WatchlistIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Fresh users per test, so tests sharing one database never see each other's watchlists.
    private final UUID seller = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private UUID auction(String title) throws Exception {
        return AuctionFixtures.createAuction(mvc, objectMapper, seller, title);
    }

    private void watch(UUID user, UUID auctionId) throws Exception {
        mvc.perform(post("/auctions/{id}/watch", auctionId).cookie(TestTokens.accessCookie(user)))
                .andExpect(status().isOk());
    }

    @Test
    void watchingTwiceIsANoOpAndStillAnswers200() throws Exception {
        UUID id = auction("Twice");

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/auctions/{id}/watch", id).cookie(TestTokens.accessCookie(alice)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.auctionId").value(id.toString()))
                    .andExpect(jsonPath("$.watching").value(true));
        }

        mvc.perform(get("/me/watchlist").cookie(TestTokens.accessCookie(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()));
    }

    @Test
    void watchingAnAuctionThatDoesNotExistIs404() throws Exception {
        mvc.perform(post("/auctions/{id}/watch", UUID.randomUUID()).cookie(TestTokens.accessCookie(alice)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Auction not found")));
    }

    /**
     * The issue text says 403, but the rest of this system (see README, "Authentication") answers an anonymous
     * request with 401 and keeps 403 for a signed-in user who is not allowed. The watchlist follows that.
     */
    @Test
    void anonymousRequestsAreRejected() throws Exception {
        UUID id = auction("Anonymous");

        mvc.perform(post("/auctions/{id}/watch", id)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/auctions/{id}/watch", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/me/watchlist")).andExpect(status().isUnauthorized());
    }

    @Test
    void watchlistOnlyContainsTheCallersOwnAuctions() throws Exception {
        UUID forAlice = auction("For Alice");
        UUID forBob = auction("For Bob");
        UUID forBoth = auction("For both");
        watch(alice, forAlice);
        watch(bob, forBob);
        watch(alice, forBoth);
        watch(bob, forBoth);

        mvc.perform(get("/me/watchlist").cookie(TestTokens.accessCookie(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", contains(forBoth.toString(), forAlice.toString())));
        mvc.perform(get("/me/watchlist").cookie(TestTokens.accessCookie(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", contains(forBoth.toString(), forBob.toString())));
    }

    @Test
    void newestAddedComesFirstAndAClientSortIsIgnored() throws Exception {
        // Titles are chosen so that sorting by title would give the opposite order to "newest added first".
        UUID alpha = auction("Alpha");
        UUID zulu = auction("Zulu");
        watch(alice, alpha);
        watch(alice, zulu);

        mvc.perform(get("/me/watchlist").cookie(TestTokens.accessCookie(alice)))
                .andExpect(jsonPath("$.content[*].id", contains(zulu.toString(), alpha.toString())));
        mvc.perform(get("/me/watchlist").param("sort", "title,asc").cookie(TestTokens.accessCookie(alice)))
                .andExpect(jsonPath("$.content[*].id", contains(zulu.toString(), alpha.toString())));
    }

    @Test
    void theListIsPaginated() throws Exception {
        UUID first = auction("Page 1");
        UUID second = auction("Page 2");
        UUID third = auction("Page 3");
        watch(alice, first);
        watch(alice, second);
        watch(alice, third);

        mvc.perform(get("/me/watchlist").param("page", "0").param("size", "2")
                        .cookie(TestTokens.accessCookie(alice)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].id", contains(third.toString(), second.toString())));
        mvc.perform(get("/me/watchlist").param("page", "1").param("size", "2")
                        .cookie(TestTokens.accessCookie(alice)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(first.toString()));
    }

    @Test
    void unwatchRemovesTheAuctionAndIsIdempotent() throws Exception {
        UUID id = auction("Unwatch me");
        watch(alice, id);

        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/auctions/{id}/watch", id).cookie(TestTokens.accessCookie(alice)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.watching").value(false));
        }

        mvc.perform(get("/me/watchlist").cookie(TestTokens.accessCookie(alice)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void unwatchingOneUsersEntryLeavesAnotherUsersEntryAlone() throws Exception {
        UUID id = auction("Shared");
        watch(alice, id);
        watch(bob, id);

        mvc.perform(delete("/auctions/{id}/watch", id).cookie(TestTokens.accessCookie(alice)))
                .andExpect(status().isOk());

        mvc.perform(get("/me/watchlist").cookie(TestTokens.accessCookie(bob)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()));
    }

    @Test
    void unwatchingAnAuctionThatDoesNotExistIs404() throws Exception {
        mvc.perform(delete("/auctions/{id}/watch", UUID.randomUUID()).cookie(TestTokens.accessCookie(alice)))
                .andExpect(status().isNotFound());
    }

    @Test
    void auctionResponsesTellEachCallerWhetherTheyWatchIt() throws Exception {
        UUID id = auction("Flagged");
        watch(alice, id);

        mvc.perform(get("/auctions/{id}", id).cookie(TestTokens.accessCookie(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watching").value(true));
        mvc.perform(get("/auctions/{id}", id).cookie(TestTokens.accessCookie(bob)))
                .andExpect(jsonPath("$.watching").value(false));
        mvc.perform(get("/auctions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watching").value(false));

        mvc.perform(get("/auctions").param("size", "100").cookie(TestTokens.accessCookie(alice)))
                .andExpect(jsonPath("$.content[?(@.id=='" + id + "')].watching").value(true));
    }
}
