package com.explorer.logic.servermanaging;

import com.explorer.logic.farm.FarmHandler;
import com.explorer.logic.laboratory.extract.LabExtractHandler;
import com.explorer.logic.laboratory.synthesize.LabSynthesizeHandler;
import com.explorer.logic.laboratory.upgrade.LabUpgradeHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestHandler {

    private final LabExtractHandler labExtractHandler;
    private final LabSynthesizeHandler synthesizeHandler;
    private final LabUpgradeHandler labUpgradeHandler;
    private final FarmHandler farmHandler;

    public Mono<Void> handleRequest(HttpServerRequest request, HttpServerResponse response) {
        // 요청이 POST 메서드인지 확인
        if (request.method().name().equalsIgnoreCase("POST")) {
            String requestPath = request.path();

            switch (requestPath) {
                case "lab/extract":
                    log.info("POST :: lab/extract");
                    return labExtractHandler.labExtractHandler(request, response);
                case "lab/synthesize":
                    log.info("POST :: lab/synthesize");
                    return synthesizeHandler.labSynthesizeHandler(request, response);
                case "lab/upgrade":
                    log.info("POST :: lab/upgrade");
                    return labUpgradeHandler.labUpgradeHandler(request, response);
                case "farm":
                    log.info("POST :: farm");
                    return farmHandler.farmHandler(request, response);
            }
        }

        // POST 요청이 아닌 경우 405 상태 반환
        log.warn("Method Not Allowed: {} on {}", request.method(), request.path());
        return response
                .status(405)
                .sendString(Mono.just("Method Not Allowed"))
                .then();

    }
}
