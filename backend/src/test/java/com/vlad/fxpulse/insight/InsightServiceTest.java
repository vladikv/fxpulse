package com.vlad.fxpulse.insight;

import com.vlad.fxpulse.common.BadRequestException;
import com.vlad.fxpulse.common.NotFoundException;
import com.vlad.fxpulse.currency.Currency;
import com.vlad.fxpulse.currency.CurrencyRepository;
import com.vlad.fxpulse.rate.RateService;
import com.vlad.fxpulse.rate.RateStatsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    private static final LocalDate FROM = LocalDate.parse("2026-10-01");
    private static final LocalDate TO = LocalDate.parse("2026-10-31");
    private static final Currency EUR = new Currency("EUR", "euro");

    @Mock
    private RateService rateService;
    @Mock
    private AiInsightRepository insightRepository;
    @Mock
    private CurrencyRepository currencyRepository;
    @Mock
    private InsightPromptBuilder promptBuilder;
    @Mock
    private AiInsightClient aiClient;

    @Mock
    private AiDailyLimit dailyLimit;

    private InsightService service() {
        return new InsightService(rateService, insightRepository, currencyRepository, promptBuilder, aiClient,
                dailyLimit);
    }

    private void givenSlotAvailable() {
        when(dailyLimit.tryAcquire()).thenReturn(true);
    }

    private static RateStatsResponse stats(int count) {
        return new RateStatsResponse("EUR", FROM, TO, count, null, null, null, null, null, null, null);
    }

    private static AiInsight stored(String content) {
        return new AiInsight(EUR, FROM, TO, content, "stored-model");
    }

    private void givenStats(int count) {
        when(rateService.getStats("eur", FROM, TO)).thenReturn(stats(count));
    }

    private void givenLookup(Optional<AiInsight> result) {
        when(insightRepository.findByCurrencyCodeAndPeriodFromAndPeriodTo("EUR", FROM, TO)).thenReturn(result);
    }

    @Test
    void getInsight_cacheHit_returnsStoredContentWithoutCallingAi() {
        // Given
        givenStats(10);
        givenLookup(Optional.of(stored("cached text")));

        // When
        InsightResponse response = service().getInsight("eur", FROM, TO);

        // Then
        assertThat(response.cached()).isTrue();
        assertThat(response.content()).isEqualTo("cached text");
        assertThat(response.model()).isEqualTo("stored-model");
        assertThat(response.code()).isEqualTo("EUR");
        verifyNoInteractions(aiClient, promptBuilder);
    }

    @Test
    void getInsight_cacheMiss_callsAiOnceAndSavesInsight() {
        // Given
        RateStatsResponse stats = stats(10);
        when(rateService.getStats("eur", FROM, TO)).thenReturn(stats);
        givenLookup(Optional.empty());
        givenSlotAvailable();
        when(promptBuilder.build(stats)).thenReturn("the prompt");
        when(aiClient.generate("the prompt")).thenReturn(new AiCompletion("fresh text", "gemini-x"));
        when(currencyRepository.getReferenceById("EUR")).thenReturn(EUR);
        when(insightRepository.saveAndFlush(any(AiInsight.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        InsightResponse response = service().getInsight("eur", FROM, TO);

        // Then
        verify(aiClient).generate("the prompt");
        ArgumentCaptor<AiInsight> captor = ArgumentCaptor.forClass(AiInsight.class);
        verify(insightRepository).saveAndFlush(captor.capture());
        AiInsight saved = captor.getValue();
        assertThat(saved.getPeriodFrom()).isEqualTo(FROM);
        assertThat(saved.getPeriodTo()).isEqualTo(TO);
        assertThat(saved.getContent()).isEqualTo("fresh text");
        assertThat(saved.getModel()).isEqualTo("gemini-x");
        assertThat(saved.getCurrency()).isSameAs(EUR);
        assertThat(response.cached()).isFalse();
        assertThat(response.content()).isEqualTo("fresh text");
        assertThat(response.model()).isEqualTo("gemini-x");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void getInsight_noRatesAndNoCache_throwsNotFoundWithoutCallingAi() {
        // Given
        givenStats(0);
        givenLookup(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service().getInsight("eur", FROM, TO))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("nothing to summarize");
        verifyNoInteractions(aiClient);
        verify(insightRepository, never()).saveAndFlush(any());
    }

    @Test
    void getInsight_cacheHitOnEmptyRange_returnsCachedRow() {
        // Given: rates may have been removed after the insight was stored
        givenStats(0);
        givenLookup(Optional.of(stored("old text")));

        // When
        InsightResponse response = service().getInsight("eur", FROM, TO);

        // Then
        assertThat(response.cached()).isTrue();
        assertThat(response.content()).isEqualTo("old text");
        verifyNoInteractions(aiClient);
    }

    @Test
    void getInsight_dailyLimitReached_throwsRateLimitedWithoutCallingAiOrSaving() {
        // Given
        givenStats(10);
        givenLookup(Optional.empty());
        when(dailyLimit.tryAcquire()).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service().getInsight("eur", FROM, TO))
                .isInstanceOf(AiRateLimitedException.class)
                .hasMessage("Daily AI insight limit reached, try again tomorrow");
        verifyNoInteractions(aiClient, promptBuilder);
        verify(insightRepository, never()).saveAndFlush(any());
    }

    @Test
    void getInsight_cacheHit_doesNotTakeDailyLimitSlot() {
        // Given
        givenStats(10);
        givenLookup(Optional.of(stored("cached text")));

        // When
        service().getInsight("eur", FROM, TO);

        // Then
        verifyNoInteractions(dailyLimit);
    }

    @Test
    void getInsight_emptyRange_doesNotTakeDailyLimitSlot() {
        // Given
        givenStats(0);
        givenLookup(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service().getInsight("eur", FROM, TO)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(dailyLimit);
    }

    @Test
    void getInsight_aiRateLimited_propagatesAndSavesNothing() {
        // Given
        givenStats(10);
        givenLookup(Optional.empty());
        givenSlotAvailable();
        when(promptBuilder.build(any())).thenReturn("p");
        when(aiClient.generate("p")).thenThrow(new AiRateLimitedException("AI rate limit reached, try again later"));

        // When / Then
        assertThatThrownBy(() -> service().getInsight("eur", FROM, TO))
                .isInstanceOf(AiRateLimitedException.class);
        verify(insightRepository, never()).saveAndFlush(any());
    }

    @Test
    void getInsight_aiUnavailable_propagatesAndSavesNothing() {
        // Given
        givenStats(10);
        givenLookup(Optional.empty());
        givenSlotAvailable();
        when(promptBuilder.build(any())).thenReturn("p");
        when(aiClient.generate("p")).thenThrow(new AiUnavailableException("AI service returned no answer"));

        // When / Then
        assertThatThrownBy(() -> service().getInsight("eur", FROM, TO))
                .isInstanceOf(AiUnavailableException.class);
        verify(insightRepository, never()).saveAndFlush(any());
    }

    @Test
    void getInsight_raceOnSave_returnsWinnersRowAsCached() {
        // Given: first lookup empty, the insert violates the unique key, second lookup finds the winner
        givenStats(10);
        when(insightRepository.findByCurrencyCodeAndPeriodFromAndPeriodTo("EUR", FROM, TO))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(stored("winner text")));
        givenSlotAvailable();
        when(promptBuilder.build(any())).thenReturn("p");
        when(aiClient.generate("p")).thenReturn(new AiCompletion("loser text", "m"));
        when(currencyRepository.getReferenceById("EUR")).thenReturn(EUR);
        when(insightRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        // When
        InsightResponse response = service().getInsight("eur", FROM, TO);

        // Then
        assertThat(response.cached()).isTrue();
        assertThat(response.content()).isEqualTo("winner text");
    }

    @Test
    void getInsight_raceOnSaveButRowStillMissing_rethrowsOriginalException() {
        // Given
        givenStats(10);
        DataIntegrityViolationException original = new DataIntegrityViolationException("other violation");
        when(insightRepository.findByCurrencyCodeAndPeriodFromAndPeriodTo("EUR", FROM, TO))
                .thenReturn(Optional.empty());
        givenSlotAvailable();
        when(promptBuilder.build(any())).thenReturn("p");
        when(aiClient.generate("p")).thenReturn(new AiCompletion("text", "m"));
        when(currencyRepository.getReferenceById("EUR")).thenReturn(EUR);
        when(insightRepository.saveAndFlush(any())).thenThrow(original);

        // When / Then
        assertThatThrownBy(() -> service().getInsight("eur", FROM, TO)).isSameAs(original);
    }

    @Test
    void getInsight_unknownCurrency_propagatesNotFoundWithoutOtherCalls() {
        // Given
        when(rateService.getStats(anyString(), any(), any())).thenThrow(new NotFoundException("Unknown currency"));

        // When / Then
        assertThatThrownBy(() -> service().getInsight("xyz", FROM, TO)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(insightRepository, currencyRepository, promptBuilder, aiClient);
    }

    @Test
    void getInsight_invalidRange_propagatesBadRequestWithoutOtherCalls() {
        // Given
        when(rateService.getStats(anyString(), any(), any())).thenThrow(new BadRequestException("bad range"));

        // When / Then
        assertThatThrownBy(() -> service().getInsight("eur", TO, FROM)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(insightRepository, currencyRepository, promptBuilder, aiClient);
    }
}
