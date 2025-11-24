/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.study;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.gridsuite.bddtests.common.EnvProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Iterator;
import java.util.Optional;

public final class StudyRequests {

    public static synchronized StudyRequests getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new StudyRequests();
        }
        return INSTANCE;
    }

    private static StudyRequests INSTANCE = null;
    private final WebClient webClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyRequests.class);

    private StudyRequests() {
        webClient = EnvProperties.getInstance().getWebClient(EnvProperties.MicroService.STUDY_SERVER);
    }

    private JsonNode findNodeInTree(JsonNode node, String studyNodeIdentifier, String identifierKey) {
        if (node.has(identifierKey) && node.get(identifierKey).asText().equalsIgnoreCase(studyNodeIdentifier)) {
            return node;
        }
        if (node.has("children")) {
            for (Iterator<JsonNode> it = node.get("children").elements(); it.hasNext(); ) {
                JsonNode subNode = it.next();
                JsonNode result = findNodeInTree(subNode, studyNodeIdentifier, identifierKey);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    public String getNodeId(String studyId, String studyNodeName) {
        String nodeId = null;
        JsonNode node = getNodeData(studyId, Optional.empty(), studyNodeName, "name");
        if (node != null && node.has("id")) {
            nodeId = node.get("id").asText();
        }
        return nodeId;
    }

    public JsonNode getNodeData(String studyId, Optional<String> rootNetworkUuid, String studyNodeIdentifier, String identifierKey) {
        JsonNode wantedNode;
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode;
        final String[] jsonString = {null};

        String path = UriComponentsBuilder.fromPath("studies/{studyId}/tree")
                .queryParamIfPresent("rootNetworkUuid", rootNetworkUuid)
                .buildAndExpand(studyId)
                .toUriString();

        webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(
                        s -> {
                            LOGGER.info("getNodeData '{}'", s);
                            jsonString[0] = s;
                        }
                ).block();

        try {
            rootNode = mapper.readTree(jsonString[0]);
            wantedNode = findNodeInTree(rootNode, studyNodeIdentifier, identifierKey);
        } catch (JsonProcessingException je) {
            return null;
        }
        return wantedNode;
    }

    public String builtStatus(String studyId, String rootNetworkUuid, String studyNodeId) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node;
        String built = null;

        String path = UriComponentsBuilder.fromPath("studies/{studyId}/tree/nodes/{id}")
                .queryParam("rootNetworkUuid", rootNetworkUuid)
                .buildAndExpand(studyId, studyNodeId)
                .toUriString();

        String jsonString = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            node = mapper.readTree(jsonString);
            if (node != null && node.has("nodeBuildStatus") && node.get("nodeBuildStatus").has("localBuildStatus")) {
                built = node.get("nodeBuildStatus").get("localBuildStatus").asText();
            }
        } catch (JsonProcessingException je) {
            return null;
        }
        return built;
    }

    public String getFirstRootNetworkId(String studyId) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode;
        final String[] jsonString = {null};
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks")
                .buildAndExpand(studyId)
                .toUriString();

        webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(
                        s -> {
                            LOGGER.info("getFirstRootNetwork '{}'", s);
                            jsonString[0] = s;
                        }
                ).block();

        try {
            rootNode = mapper.readTree(jsonString[0]);
            return rootNode.elements().next().get("rootNetworkUuid").asText();
        } catch (JsonProcessingException je) {
            return null;
        }
    }

    public void updateSwitch(String switchId, String studyId, String nodeId, boolean openState) {
        // create json body
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("equipmentAttributeName", "open");
        body.put("equipmentAttributeValue", openState);
        body.put("equipmentId", switchId);
        body.put("equipmentType", "SWITCH");
        body.put("type", "EQUIPMENT_ATTRIBUTE_MODIFICATION");
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/nodes/{nodeUuid}/network-modifications")
                .buildAndExpand(studyId, nodeId)
                .toUriString();
        LOGGER.info("updateSwitch uri: '{}'", path);

        webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .body(BodyInserters.fromValue(body.toString()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void runLoadFlow(String studyId, String rootNetworkUuid, String nodeId, int limitReduction) {
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/run?limitReduction={limitReduction}")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId, limitReduction / 100.)
                .toUriString();
        LOGGER.info("runLoadFlow uri: '{}'", path);

        webClient.put()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void setComputationParameters(String studyId, String computationName, String resourceFileContent) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/{computationName}/parameters")
                .buildAndExpand(studyId, computationName)
                .toUriString();
        LOGGER.info("setComputationParameters uri: '{}'", path);
        webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .body(BodyInserters.fromValue(resourceFileContent))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void setLoadFlowProvider(String provider, String studyId) {
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/loadflow/provider")
                .buildAndExpand(studyId)
                .toUriString();
        LOGGER.info("setLoadFlowProvider with {} uri: '{}'", provider, path);

        webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .body(BodyInserters.fromValue(provider))// body contains only the provider name
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String getLoadFlowInfos(String studyId, String rootNetworkUuid, String nodeId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}/loadflow/status")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId)
                .toUriString();
        LOGGER.info("getLoadFlowInfos uri: '{}'", path);
        String status = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return status == null ? "NOT_DONE" : status;
    }
}

