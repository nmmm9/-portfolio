package com.impacttracker.backend.ingest.publicdata;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class PublicDataClient {
    private final WebClient web = WebClient.create();

    public Mono<String> call(String url, String serviceKey, Map<String,String> params) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("serviceKey", serviceKey);
        params.forEach(b::queryParam);
        return web.get().uri(b.build(true).toUri()).retrieve().bodyToMono(String.class);
    }
}
