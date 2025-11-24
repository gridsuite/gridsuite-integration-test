/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.cases;

import org.gridsuite.bddtests.common.EnvProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

public final class CaseRequests {

    public static synchronized CaseRequests getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CaseRequests();
        }
        return INSTANCE;
    }

    private static CaseRequests INSTANCE = null;
    private final WebClient webClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseRequests.class);

    private CaseRequests() {
        webClient = EnvProperties.getInstance().getWebClient(EnvProperties.MicroService.CASE_SERVER);
    }

    public boolean existsCase(String caseId) {
        final boolean[] exists = {false};

        String path = UriComponentsBuilder.fromPath("cases/{caseId}/exists")
                .buildAndExpand(caseId)
                .toUriString();

        // iterate through the stream (just true/false expected)
        webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(
                        s -> {
                            LOGGER.info("existsCase '{}'", s);
                            exists[0] = s.equalsIgnoreCase("true");
                        }
                ).block();

        return exists[0];
    }
}
