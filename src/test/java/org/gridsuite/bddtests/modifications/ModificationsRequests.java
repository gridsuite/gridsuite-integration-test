/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.modifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.bddtests.common.EnvProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

public final class ModificationsRequests {

    public static synchronized ModificationsRequests getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ModificationsRequests();
        }
        return INSTANCE;
    }

    private static ModificationsRequests INSTANCE = null;
    private final WebClient webClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(ModificationsRequests.class);

    private ModificationsRequests() {
        webClient = EnvProperties.getInstance().getWebClient(EnvProperties.MicroService.MODIFICATION_SERVER);
    }

    public JsonNode getModification(String modificationId) {
        String path = UriComponentsBuilder.fromPath("network-modifications/{modificationId}")
                .buildAndExpand(modificationId)
                .toUriString();
        LOGGER.info("getModification {}", path);
        String jsonResponse = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        LOGGER.info("getModification resp: '{}'", jsonResponse);
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
