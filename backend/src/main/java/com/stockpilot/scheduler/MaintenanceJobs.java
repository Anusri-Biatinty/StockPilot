package com.stockpilot.scheduler;

import com.stockpilot.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceJobs {

    private final RefreshTokenRepository refreshTokenRepository;

    /** Deletes refresh tokens that expired more than a day ago (kept briefly to detect reuse). */
    @Scheduled(cron = "${app.jobs.token-cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
        log.info("Purged {} expired refresh tokens", deleted);
    }
}
