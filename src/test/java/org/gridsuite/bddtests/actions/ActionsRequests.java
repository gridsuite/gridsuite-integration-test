/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.actions;

import org.gridsuite.bddtests.common.EnvProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

public final class ActionsRequests {

    public static synchronized ActionsRequests getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ActionsRequests();
        }
        return INSTANCE;
    }

    private static ActionsRequests INSTANCE = null;
    private final WebClient webClient;
    private final String version = "v1";

    private static final Logger LOGGER = LoggerFactory.getLogger(ActionsRequests.class);

    private ActionsRequests() {
        webClient = EnvProperties.getInstance().getWebClient(EnvProperties.MicroService.ActionServer);
    }

    public void updateFormContingencyList(String listId, String fileContent) {
        String path = UriComponentsBuilder.fromPath(
                        "form-contingency-lists/{listId}")
                .buildAndExpand(listId)
                .toUriString();
        LOGGER.info("updateFormContingencyList uri: '{}'", path);

        webClient.put()
                .uri(path)
                .body(BodyInserters.fromValue(fileContent))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
