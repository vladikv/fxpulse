package com.vlad.fxpulse.insight;

import com.vlad.fxpulse.common.NotFoundException;
import com.vlad.fxpulse.currency.CurrencyRepository;
import com.vlad.fxpulse.rate.RateService;
import com.vlad.fxpulse.rate.RateStatsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Returns an AI trend summary for a currency and period, calling the AI at most once per
 * (currency, from, to) and data state: the answer is stored in {@code ai_insight} with the number of
 * rates it was based on and reused while that number is unchanged. If more rates exist later (the period
 * was requested before NBP published its last day, or a backfill filled gaps), the text is regenerated.
 *
 * <p>Deliberately NOT {@code @Transactional}: the AI call takes seconds, and a transaction around it
 * would hold a DB connection the whole time. Each repository call runs in its own short transaction.
 */
@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);

    private final RateService rateService;
    private final AiInsightRepository insightRepository;
    private final CurrencyRepository currencyRepository;
    private final InsightPromptBuilder promptBuilder;
    private final AiInsightClient aiClient;
    private final AiDailyLimit dailyLimit;

    public InsightService(RateService rateService, AiInsightRepository insightRepository,
                          CurrencyRepository currencyRepository, InsightPromptBuilder promptBuilder,
                          AiInsightClient aiClient, AiDailyLimit dailyLimit) {
        this.rateService = rateService;
        this.insightRepository = insightRepository;
        this.currencyRepository = currencyRepository;
        this.promptBuilder = promptBuilder;
        this.aiClient = aiClient;
        this.dailyLimit = dailyLimit;
    }

    public InsightResponse getInsight(String code, LocalDate from, LocalDate to) {
        // Reuses all rate rules: code normalisation, 404 for unknown codes, default dates, max range.
        // The resolved from/to are the cache key, so "no dates" and explicit dates hit the same row.
        RateStatsResponse stats = rateService.getStats(code, from, to);

        Optional<AiInsight> cached = insightRepository.findByCurrencyCodeAndPeriodFromAndPeriodTo(
                stats.code(), stats.from(), stats.to());
        if (cached.isPresent() && cached.get().isUpToDate(stats.count())) {
            log.debug("Insight cache hit for {} {}..{}", stats.code(), stats.from(), stats.to());
            return InsightResponse.of(stats.code(), cached.get(), true);
        }

        if (stats.count() == 0) {
            throw new NotFoundException("No " + stats.code() + " rates between " + stats.from()
                    + " and " + stats.to() + "; nothing to summarize");
        }

        // Every new (from, to) pair is a cache miss, so without a cap a client could vary the dates
        // and use up the whole free-tier quota. Checked only here: cache hits never count.
        if (!dailyLimit.tryAcquire()) {
            throw new AiRateLimitedException("Daily AI insight limit reached, try again tomorrow");
        }
        AiCompletion completion = aiClient.generate(promptBuilder.build(stats));
        log.info("Generated insight for {} {}..{} with {}", stats.code(), stats.from(), stats.to(),
                completion.model());

        if (cached.isPresent()) {
            // Same key, more data than when the text was written: update the existing row.
            AiInsight stale = cached.get();
            log.info("Insight for {} {}..{} was based on {} rates, now {}: regenerated", stats.code(),
                    stats.from(), stats.to(), stale.getRateCount(), stats.count());
            stale.refresh(completion.text(), completion.model(), stats.count());
            return InsightResponse.of(stats.code(), insightRepository.saveAndFlush(stale), false);
        }

        AiInsight insight = new AiInsight(currencyRepository.getReferenceById(stats.code()),
                stats.from(), stats.to(), completion.text(), completion.model(), stats.count());
        try {
            return InsightResponse.of(stats.code(), insightRepository.saveAndFlush(insight), false);
        } catch (DataIntegrityViolationException e) {
            // Two first requests for the same key ran at the same time; the unique constraint let only
            // one insert through. Return the winner's row instead of failing.
            return insightRepository.findByCurrencyCodeAndPeriodFromAndPeriodTo(
                            stats.code(), stats.from(), stats.to())
                    .map(existing -> InsightResponse.of(stats.code(), existing, true))
                    .orElseThrow(() -> e);
        }
    }
}
