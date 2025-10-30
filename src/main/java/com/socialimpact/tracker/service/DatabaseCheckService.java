package com.socialimpact.tracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseCheckService {

    @EventListener(ApplicationReadyEvent.class)
    public void checkDatabaseAfterStartup() {
        log.info("=================================================");
        log.info("Application ready to serve API requests");
        log.info("API Base URL: http://localhost:8080/api");
        log.info("=================================================");
    }
}