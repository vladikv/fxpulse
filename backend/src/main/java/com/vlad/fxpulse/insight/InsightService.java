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
 * (currency, from, to): the first answer is stored in {@code ai_insight} and reused afterwards.
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
        if (cached.isPresent()) {
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

        AiInsight insight = new AiInsight(currencyRepository.getReferenceById(stats.code()),
                stats.from(), stats.to(), completion.text(), completion.model());
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
