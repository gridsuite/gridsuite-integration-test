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
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import java.nio.file.Path;
import java.util.Iterator;
import static java.nio.file.StandardOpenOption.CREATE;

public final class StudyRequests {

    public static synchronized StudyRequests getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new StudyRequests();
        }
        return INSTANCE;
    }

    private static StudyRequests INSTANCE = null;
    private final WebClient webClient;
    private final String version = "v1";

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyRequests.class);

    private StudyRequests() {
        webClient = EnvProperties.getInstance().getWebClient(EnvProperties.MicroService.StudyServer);
    }

    public boolean existsStudy(String studyId) {
        boolean ok;
        // Just need the HEAD query and a proper 200 http return code
        String studyPath = UriComponentsBuilder.fromPath("studies/{studyId}")
                .buildAndExpand(studyId)
                .toUriString();
        try {
            WebClient.ResponseSpec response = webClient.head()
                    .uri(studyPath)
                    .retrieve();
            ok = HttpStatus.OK == response.toBodilessEntity().block().getStatusCode();
        } catch (Exception e) {
            ok = false;
        }
        return ok;
    }

    private JsonNode findNodeInTree(JsonNode node, String studyNodeIdentifier, String identifierKey) {
        if (node.has(identifierKey) && node.get(identifierKey).asText().equalsIgnoreCase(studyNodeIdentifier)) {
            return node;
        }
        if (node.has("children")) {
            for (Iterator<JsonNode> it = node.get("children").elements(); it.hasNext();) {
                JsonNode subNode = it.next();
                return findNodeInTree(subNode, studyNodeIdentifier, identifierKey);
            }
        }
        return null;
    }

    public String getNodeId(String studyId, String studyNodeName) {
        String nodeId = null;
        JsonNode node = getNodeData(studyId, studyNodeName, "name");
        if (node != null && node.has("id")) {
            nodeId = node.get("id").asText();
        }
        return nodeId;
    }

    public JsonNode getNodeData(String studyId, String studyNodeIdentifier, String identifierKey) {
        JsonNode wantedNode;
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode;
        final String[] jsonString = {null};

        String path = UriComponentsBuilder.fromPath("studies/{studyId}/tree")
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

    public boolean checkNodeTree(String studyId) {
        boolean ok;
        // Just need the HEAD query and a proper 200 http return code
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/tree")
                .buildAndExpand(studyId)
                .toUriString();
        try {
            WebClient.ResponseSpec response = webClient.head()
                .uri(path)
                .retrieve();
            ok = HttpStatus.OK == response.toBodilessEntity().block().getStatusCode();
        } catch (Exception e) {
            ok = false;
        }
        return ok;
    }

    // TODO not used
    public Equipment searchEqt(String studyId, String nodeId, String searchInput, String fieldSelector, boolean inUpstreamBuiltParentNode, String euipmentType) {
        final Equipment[] foundEquipment = {null};

        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/search?userInput={searchInput}&fieldSelector={fieldSelector}&inUpstreamBuiltParentNode={up}&equipmentType={eltType}")
                .buildAndExpand(studyId, nodeId, searchInput, fieldSelector, inUpstreamBuiltParentNode, euipmentType)
                .toUriString();
        LOGGER.info("searchEqt uri: '{}'", path);

        // iterate through the stream, looking for first exact matching
        webClient.get()
                .uri(path)
                .retrieve()
                .bodyToFlux(Equipment.class)
                .doOnNext(
                        equipment -> LOGGER.info("searchEquipment '{}'", equipment.toString())
                )
                .takeUntil(equipment -> {
                            if (equipment.getIdentifier(fieldSelector).equalsIgnoreCase(searchInput)) {
                                foundEquipment[0] = equipment;
                                return true;    // exit condition (flux disposal)
                            } else {
                                return false;
                            }
                        }
                )
                .blockLast(); // this is a blocking subscribe

        return foundEquipment[0];
    }

    public JsonNode searchEquipment(String studyId, String nodeId, String searchInput, String fieldSelector, boolean inUpstreamBuiltParentNode, String equipmentType) {

        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/search?userInput={searchInput}&fieldSelector={fieldSelector}&inUpstreamBuiltParentNode={up}")
                .buildAndExpand(studyId, nodeId, searchInput, fieldSelector, inUpstreamBuiltParentNode)
                .toUriString();
        if (!equipmentType.isEmpty()) {
            String pathWithType = UriComponentsBuilder.fromPath("&equipmentType={eltType}")
                    .buildAndExpand(equipmentType)
                    .toUriString();
            path += pathWithType;
        }
        LOGGER.info("searchEquipment uri: '{}'", path);

        String response = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToFlux(String.class)
                .blockLast(); // this is a blocking subscribe

        // parse Json data
        try {
            ObjectMapper mapper = new ObjectMapper();
            if (response != null) {
                return mapper.readTree(response);
            }
        } catch (JsonProcessingException je) {
            return null;
        }
        return null;
    }

    private JsonNode getNetworkMapData(String path, String equipmentId) {
        final String[] jsonString = {null};

        // retrieve Json raw data as string
        webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(
                        s -> {
                            LOGGER.info("getNetworkMapData '{}'", s);
                            jsonString[0] = s;
                        }
                ).block();

        // parse Json data
        try {
            ObjectMapper mapper = new ObjectMapper();
            if (jsonString[0] != null) {
                JsonNode rootNode = mapper.readTree(jsonString[0]);
                // single element or list of elements ?
                if (!rootNode.isArray()) {
                    if (rootNode.has("id") && rootNode.get("id").asText().equalsIgnoreCase(equipmentId)) {
                        return rootNode;
                    }
                } else {
                    for (Iterator<JsonNode> it = rootNode.elements(); it.hasNext(); ) {
                        JsonNode eltNode = it.next();
                        if (eltNode.has("id") && eltNode.get("id").asText().equalsIgnoreCase(equipmentId)) {
                            return eltNode;
                        }
                    }
                }
            }
        } catch (JsonProcessingException je) {
            return null;
        }
        return null;
    }

    public JsonNode getEquipmentData(String studyId, String nodeId, String equipmentType, String equipmentId, boolean inUpstreamBuiltParentNode) {
        // this request should return a single element with equipmentId
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/network-map/" + equipmentType + "/{eqptId}?inUpstreamBuiltParentNode={up}")
                .buildAndExpand(studyId, nodeId, equipmentId, inUpstreamBuiltParentNode)
                .toUriString();
        return getNetworkMapData(path, equipmentId);
    }

    public JsonNode getEquipmentData(String studyId, String nodeId, String equipmentType, String equipmentId, String substationId) {
        // this request may return N elements with a given stationId, then we'll have to search for the element having equipmentId
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/network-map/" + equipmentType + "?substationId={station}")
                .buildAndExpand(studyId, nodeId, substationId)
                .toUriString();
        return getNetworkMapData(path, equipmentId);
    }

    public void updateSwitch(String switchId, String studyId, String nodeId, boolean openState) {
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open={openState}")
                .buildAndExpand(studyId, nodeId, switchId, openState)
                .toUriString();
        LOGGER.info("updateSwitch uri: '{}'", path);

        webClient.put()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void runLoadFlow(String studyId, String nodeId) {
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/nodes/{nodeUuid}/loadflow/run")
                .buildAndExpand(studyId, nodeId)
                .toUriString();
        LOGGER.info("runLoadFlow uri: '{}'", path);

        webClient.put()
                .uri(path)
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
                .body(BodyInserters.fromValue(provider))// body contains only the provider name
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public JsonNode getLoadFlowInfos(String studyId, String nodeId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/loadflow/infos")
                .buildAndExpand(studyId, nodeId)
                .toUriString();
        LOGGER.info("getLoadFlowInfos uri: '{}'", path);
        String jsonResponse = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();

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

    public String getSecurityAnalysisStatus(String studyId, String nodeId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/security-analysis/status")
                .buildAndExpand(studyId, nodeId)
                .toUriString();
        return webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public JsonNode getSecurityAnalysisResult(String studyId, String nodeId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/security-analysis/result")
                .buildAndExpand(studyId, nodeId)
                .toUriString();
        LOGGER.info("getSecurityAnalysisResult uri: '{}'", path);
        String jsonResponse = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        //LOGGER.info("getSecurityAnalysisResult resp: '{}'", jsonResponse);
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

    public JsonNode contingencyCount(String studyId, String nodeId, String listId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/contingency-count?contingencyListName={listId}")
            .buildAndExpand(studyId, nodeId, listId)
            .toUriString();
        LOGGER.info("contingencyCount uri: '{}'", path);
        String jsonResponse = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        result.put("count", jsonResponse);
        return result;
    }

    public void runSecurityAnalysis(String studyId, String nodeId, String listId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/security-analysis/run?contingencyListName={listId}")
                .buildAndExpand(studyId, nodeId, listId)
                .toUriString();
        LOGGER.info("runSecurityAnalysis uri: '{}'", path);
        String jsonResponse = webClient.post()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        LOGGER.info("runSecurityAnalysis resp: '{}'", jsonResponse);
    }

    public void deleteEquipment(String studyId, String nodeId, String eqptDeleteType, String equipmentId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/network-modification/equipments/type/{eqptType}/id/{eqptId}")
                .buildAndExpand(studyId, nodeId, eqptDeleteType, equipmentId)
                .toUriString();
        LOGGER.info("deleteEquipment uri: '{}'", path);
        webClient.delete()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void upsertEquipment(String studyId, String nodeId, String equipmentType, String resourceFileContent, boolean creation) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/network-modification/" + equipmentType)
                .buildAndExpand(studyId, nodeId, equipmentType)
                .toUriString();
        if (creation) {
            LOGGER.info("createEquipment {} uri: '{}'", equipmentType, path);
            webClient.post()
                    .uri(path)
                    .body(BodyInserters.fromValue(resourceFileContent))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } else {
            LOGGER.info("updateEquipment {} uri: '{}'", equipmentType, path);
            webClient.put()
                    .uri(path)
                    .body(BodyInserters.fromValue(resourceFileContent))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        }
    }

    public int deleteNode(String studyId, String nodeId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/tree/nodes/{nodeId}")
                .buildAndExpand(studyId, nodeId)
                .toUriString();
        LOGGER.info("deleteNode uri: '{}'", path);
        try {
            String resp = webClient.delete()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException resp) {
            return resp.getStatusCode().value();
        }
        return 200;
    }

    public void createNode(String studyId, String nodeId, String newNodeName, String creationMode) {
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/tree/nodes/{nodeId}?mode={creationMode}")
                .buildAndExpand(studyId, nodeId, creationMode)
                .toUriString();
        LOGGER.info("createNode uri: '{}'", path);

        // create body (json tree)
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("buildStatus", "NOT_BUILT");
        body.put("name", newNodeName);
        body.put("type", "NETWORK_MODIFICATION");

        webClient.post()
                .uri(path)
                .body(BodyInserters.fromValue(body.toString()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public JsonNode exportFormatList() {
        String jsonResponse = webClient.get()
                .uri("export-network-formats")
                .retrieve()
                .bodyToMono(String.class)
                .block();
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

    public void exportCase(String studyId, String nodeId, String format, Path filePath) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/export-network/{fmt}")
                .buildAndExpand(studyId, nodeId, format)
                .toUriString();

        Flux<DataBuffer> result = webClient.get()
                .uri(path)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .bodyToFlux(DataBuffer.class);

        // save the flux into the chosen file
        LOGGER.info("Saving case flux into {} ...", filePath);
        DataBufferUtils
                .write(result, filePath, CREATE)
                .block();
    }
}

