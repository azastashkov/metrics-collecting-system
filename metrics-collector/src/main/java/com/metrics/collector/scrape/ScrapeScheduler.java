package com.metrics.collector.scrape;

import com.metrics.collector.config.CollectorProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScrapeScheduler {

    private final CollectorProperties properties;
    private final ScrapeService scrapeService;

    @PostConstruct
    public void init() {
        if (properties.getTargets() == null || properties.getTargets().isEmpty()) {
            log.warn("No scrape targets configured");
            return;
        }

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(
                properties.getTargets().size(),
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("scrape-scheduler");
                    return t;
                }
        );

        for (CollectorProperties.ScrapeTarget target : properties.getTargets()) {
            log.info("Scheduling scrape for {} every {} ms", target.getUrl(), target.getIntervalMs());
            executor.scheduleAtFixedRate(
                    () -> {
                        try {
                            scrapeService.scrape(target);
                        } catch (Exception e) {
                            log.error("Scrape failed for {}: {}", target.getUrl(), e.getMessage(), e);
                        }
                    },
                    0,
                    target.getIntervalMs(),
                    TimeUnit.MILLISECONDS
            );
        }
    }
}
