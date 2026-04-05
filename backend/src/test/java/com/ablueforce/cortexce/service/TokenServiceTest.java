package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenService.
 * Tests token calculation and economics without requiring Spring context.
 * Note: getWorkEmoji depends on ModeService (Spring-injected) and is tested
 * via integration tests instead.
 */
class TokenServiceTest {

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        // TokenService has a dependency on ModeService for getWorkEmoji.
        // We test only the token-calculation methods here; getWorkEmoji
        // is covered by integration tests (ModeService integration).
        tokenService = new TokenService(null);
    }

    // --- calculateObservationTokens tests ---

    @Test
    void calculateObservationTokens_allFieldsNull() {
        ObservationEntity obs = new ObservationEntity();
        int tokens = tokenService.calculateObservationTokens(obs);
        // All null: title=0, subtitle=0, content=0, facts serializes to "null" (4 chars)
        // size = 4, ceil(4/4) = 1
        assertEquals(1, tokens);
    }

    @Test
    void calculateObservationTokens_onlyTitle() {
        ObservationEntity obs = new ObservationEntity();
        obs.setTitle("Hello");
        // size = 5, ceil(5/4) = 2
        assertEquals(2, tokenService.calculateObservationTokens(obs));
    }

    @Test
    void calculateObservationTokens_allFieldsPopulated() {
        ObservationEntity obs = new ObservationEntity();
        obs.setTitle("Bug Fix");             // 8 chars
        obs.setSubtitle("Fix login");         // 9 chars
        obs.setContent("Fixed the issue");    // 17 chars
        obs.setFacts(List.of("fixed bug"));   // serializes to 13 or 14 chars
        int tokens = tokenService.calculateObservationTokens(obs);
        // size = 8+9+17+? = ?, ceil(?/4) = ?
        assertTrue(tokens >= 11 && tokens <= 14,
            "Expected 11-14 tokens but got " + tokens);
    }

    @Test
    void calculateObservationTokens_factsAsJsonList() {
        ObservationEntity obs = new ObservationEntity();
        obs.setTitle("Test");
        obs.setFacts(List.of("fact1", "fact2", "fact3"));
        // "Test"=4, JSON for list=25 chars, total=29, ceil(29/4)=8
        assertEquals(8, tokenService.calculateObservationTokens(obs));
    }

    @Test
    void calculateObservationTokens_largeObservation_completesWithoutOverflow() {
        ObservationEntity obs = new ObservationEntity();
        // 500K chars per text field; list of 500K chars serializes to 500,004 chars
        String large = "x".repeat(500_000);
        obs.setTitle(large);
        obs.setSubtitle(large);
        obs.setContent(large);
        obs.setFacts(List.of(large));
        // Verify it completes without throwing and produces correct token count
        int tokens = tokenService.calculateObservationTokens(obs);
        // total chars = 500K*3 + 500004 = 2,000,004; ceil/4 = 500,001
        assertEquals(500_001, tokens);
    }

    // --- calculateEconomics tests ---

    @Test
    void calculateEconomics_emptyList() {
        TokenService.TokenEconomics eco = tokenService.calculateEconomics(Collections.emptyList());
        assertEquals(0, eco.totalObservations());
        assertEquals(0, eco.totalReadTokens());
        assertEquals(0, eco.totalDiscoveryTokens());
        assertEquals(0, eco.savings());
        assertEquals(0.0, eco.savingsPercent());
    }

    @Test
    void calculateEconomics_singleObservation() {
        ObservationEntity obs = new ObservationEntity();
        obs.setTitle("Hi");          // 2 chars -> ceil(2/4)=1 token
        obs.setDiscoveryTokens(100);
        TokenService.TokenEconomics eco = tokenService.calculateEconomics(List.of(obs));
        assertEquals(1, eco.totalObservations());
        assertEquals(1, eco.totalReadTokens());
        assertEquals(100, eco.totalDiscoveryTokens());
        assertEquals(99, eco.savings());
        assertEquals(99.0, eco.savingsPercent());
    }

    @Test
    void calculateEconomics_multipleObservations() {
        ObservationEntity a = new ObservationEntity();
        a.setTitle("Hi");   // 2 -> 1 token, discovery=50
        a.setDiscoveryTokens(50);
        ObservationEntity b = new ObservationEntity();
        b.setTitle("Hello"); // 5 -> 2 tokens, discovery=100
        b.setDiscoveryTokens(100);
        TokenService.TokenEconomics eco = tokenService.calculateEconomics(List.of(a, b));
        assertEquals(2, eco.totalObservations());
        assertEquals(3, eco.totalReadTokens()); // 1+2
        assertEquals(150, eco.totalDiscoveryTokens()); // 50+100
        assertEquals(147, eco.savings());
        // savingsPercent = round(147/150*100) = round(98) = 98.0
        assertEquals(98.0, eco.savingsPercent());
    }

    @Test
    void calculateEconomics_zeroDiscoveryTokens() {
        ObservationEntity obs = new ObservationEntity();
        obs.setTitle("Test");
        obs.setDiscoveryTokens(0);
        TokenService.TokenEconomics eco = tokenService.calculateEconomics(List.of(obs));
        // savingsPercent = 0 when discoveryTokens = 0 (avoids division by zero)
        assertEquals(0.0, eco.savingsPercent());
    }

    @Test
    void calculateEconomics_nullDiscoveryTokens_treatedAsZero() {
        ObservationEntity obs = new ObservationEntity();
        obs.setTitle("Test");
        // discoveryTokens null
        TokenService.TokenEconomics eco = tokenService.calculateEconomics(List.of(obs));
        assertEquals(0, eco.totalDiscoveryTokens());
    }

    @Test
    void calculateEconomics_savingsPercent_rounded() {
        ObservationEntity obs = new ObservationEntity();
        obs.setTitle("Hi");  // 1 read token
        obs.setDiscoveryTokens(3);  // savings=2, percent=2/3*100=66.67 -> round=67
        TokenService.TokenEconomics eco = tokenService.calculateEconomics(List.of(obs));
        assertEquals(67.0, eco.savingsPercent());
    }
}
