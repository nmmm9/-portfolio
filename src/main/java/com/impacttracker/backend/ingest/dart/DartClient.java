package com.impacttracker.backend.ingest.dart;

import com.fasterxml.jackson.databind.JsonNode;
import com.impacttracker.backend.config.OpendartProps;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class DartClient {
    private final WebClient web;
    private final OpendartProps props;

    public DartClient(OpendartProps props, WebClient.Builder builder) {
        this.props = props;
        this.web = builder.baseUrl(props.getBaseUrl() + "/api").build();
    }

    public Mono<JsonNode> listReports(String corpCode, String bgnDe, String endDe) {
        return web.get()
                .uri(uri -> uri.path("/list.json")
                        .queryParam("crtfc_key", props.getApiKey())
                        .queryParam("corp_code", corpCode)
                        .queryParam("bgn_de", bgnDe)
                        .queryParam("end_de", endDe)
                        .build())
                .retrieve().bodyToMono(JsonNode.class);
    }

    public Mono<byte[]> fetchDocument(String rcpNo) {
        String url = props.getBaseUrl() + "/dsaf001/main.do?rcpNo=" + rcpNo;
        return WebClient.create(url).get().retrieve().bodyToMono(byte[].class);
    }
}
