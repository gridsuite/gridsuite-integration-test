package org.gridsuite.bddtests.config;

import org.gridsuite.bddtests.common.EnvProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

public final class ConfigRequests {

    public static synchronized ConfigRequests getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ConfigRequests();
        }
        return INSTANCE;
    }

    private static ConfigRequests INSTANCE = null;
    private final WebClient webClient;
    private final String version = "v1";

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigRequests.class);

    private ConfigRequests() {
        webClient = EnvProperties.getInstance().getWebClient(EnvProperties.MicroService.CONFIG_SERVER);
    }

    public <T> void setParameter(String paramName, T paramValue) {
        String path = UriComponentsBuilder.fromPath(
                        "applications/study/parameters/{paramName}?value={paramValue}")
                .buildAndExpand(paramName, paramValue)
                .toUriString();
        LOGGER.info("run setParameter uri: '{}'", path);

        webClient.put()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
