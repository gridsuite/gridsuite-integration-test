/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.networkconversion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.bddtests.common.EnvProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

public final class NetworkConversionRequests {

    public static synchronized NetworkConversionRequests getInstance() {
        if (instance == null) {
            instance = new NetworkConversionRequests();
        }
        return instance;
    }

    private static NetworkConversionRequests instance = null;
    private final WebClient webClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkConversionRequests.class);

    private NetworkConversionRequests() {
        webClient = EnvProperties.getInstance().getWebClient(EnvProperties.MicroService.NETWORK_CONVERSION_SERVER);
    }

    public JsonNode getImportParameters(String caseId) {
        String path = UriComponentsBuilder.fromPath(
                        "cases/{caseId}/import-parameters")
                .buildAndExpand(caseId)
                .toUriString();
        LOGGER.info("getImportParameters uri: '{}'", path);
        String jsonResponse = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        LOGGER.info("getImportParameters resp: '{}'", jsonResponse);
        // parse Json data
        if (jsonResponse != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readTree(jsonResponse);
            } catch (JsonProcessingException je) {
                return null;
            }
        }
        return null;
    }
}
