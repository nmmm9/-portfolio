package com.impacttracker.backend.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class DartClientConfig {

    @Bean
    public WebClient dartWebClient() {
        // Reactor Netty 클라이언트: 연결/읽기 타임아웃 + 핸들러
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)         // 연결 10초
                .responseTimeout(Duration.ofSeconds(20))                      // 응답 헤더 20초
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))   // 소켓 읽기 30초
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS))  // 소켓 쓰기 30초
                );

        // corpCode.zip이 제법 큼 → 메모리 버퍼 여유
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)) // 16MB
                .build();

        return WebClient.builder()
                .baseUrl("https://opendart.fss.or.kr")
                .exchangeStrategies(strategies)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
