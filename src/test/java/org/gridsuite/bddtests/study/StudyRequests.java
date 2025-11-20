package org.gridsuite.bddtests.study;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.gridsuite.bddtests.common.EnvProperties;
import org.gridsuite.bddtests.modifications.ModificationsRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

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
        webClient = EnvProperties.getInstance().getWebClient(EnvProperties.MicroService.STUDY_SERVER);
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

    public JsonNode searchEquipment(String studyId, String rootNetworkUuid, String nodeId, String searchInput, String fieldSelector, boolean inUpstreamBuiltParentNode, String equipmentType) {

        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}/search?userInput={searchInput}&fieldSelector={fieldSelector}&inUpstreamBuiltParentNode={up}")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId, searchInput, fieldSelector, inUpstreamBuiltParentNode)
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

    public JsonNode getEquipmentData(String studyId, String rootNetworkUuid, String nodeId, String equipmentType, String equipmentId, String infoType) {
        // this request should return a single element with equipmentId
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}//network/elements/{elementId}?elementType={equipmentType}&infoType={infoType}&inUpstreamBuiltParentNode=false")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId, equipmentId, equipmentType, infoType)
                .toUriString();
        LOGGER.info("getEquipmentData Uri: '{}'", path);
        String jsonResponse = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        if (jsonResponse != null) {
            LOGGER.info("getEquipmentData resp: '{}'", jsonResponse);
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readTree(jsonResponse);
            } catch (JsonProcessingException je) {
                return null;
            }
        }
        return null;
    }

    // this is used for monitoring only, we don't return anything to prevent DataBufferLimitException due to very large returned JSON
    public void getAllEquipmentsData(String studyId, String rootNetworkUuid, String nodeId, String equipmentType, String infoType) {
        // this request should return all elements of type equipmentType
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}/network/elements?elementType={equipmentType}&infoType={infoType}")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId, equipmentType, infoType)
                .toUriString();
        LOGGER.info("getAllEquipmentsData Uri: '{}'", path);
        webClient.post()
                .uri(path)
                .retrieve()
                .toBodilessEntity()
                .block();
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

    public void buildNode(String studyId, String rootNetworkUuid, String nodeId) {
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/build")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId)
                .toUriString();
        LOGGER.info("buildNode uri: '{}'", path);

        webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void unbuildNode(String studyId, String rootNetworkUuid, String nodeId) {
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/unbuild")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId)
                .toUriString();
        LOGGER.info("unbuildNode uri: '{}'", path);

        webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
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

    public String getSecurityAnalysisStatus(String studyId, String rootNetworkUuid, String nodeId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}/security-analysis/status")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId)
                .toUriString();
        return webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public JsonNode getSecurityAnalysisResult(String studyId, String rootNetworkUuid, String nodeId, String resultTypeQuery) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}/security-analysis/result?resultType={resultTypeQuery}")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId, resultTypeQuery)
                .toUriString();
        LOGGER.info("getSecurityAnalysisResult uri: '{}'", path);
        String jsonResponse = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        LOGGER.info("getSecurityAnalysisResult resp: '{}'", jsonResponse);
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

    public JsonNode getSld(String studyId, String rootNetworkUuid, String nodeId, String vlName) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}/network/voltage-levels/{vlName}/svg-and-metadata?useName=true&centerLabel=false&diagonalLabel=true&topologicalColoring=true&sldDisplayMode=STATE_VARIABLE&language=fr")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId, vlName)
                .toUriString();
        LOGGER.info("getSld uri: '{}'", path);
        String jsonResponse = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        LOGGER.info("getSld resp: '{}'", jsonResponse);
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

    public JsonNode getNad(String studyId, String rootNetworkUuid, String nodeId, List<String> vlList) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}/network-area-diagram")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId).toUriString();

        LOGGER.info("getNad uri: '{}'", path);
        Map<String, Object> requestBody = Map.of(
                "voltageLevelIds", vlList,
                "nadPositionsGenerationMode", "GEOGRAPHICAL_COORDINATES");

        String jsonResponse = webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        LOGGER.info("getNad resp: '{}'", jsonResponse);
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

    public JsonNode getLoadFlowResult(String studyId, String rootNetworkUuid, String nodeId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}/loadflow/result")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId)
                .toUriString();
        LOGGER.info("getLoadFlowResult uri: '{}'", path);
        String jsonResponse = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        LOGGER.info("getLoadFlowResult resp: '{}'", jsonResponse);
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

    public JsonNode getLimitViolationsResult(String studyId, String rootNetworkUuid, String nodeId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}/limit-violations")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId)
                .toUriString();
        LOGGER.info("getLimitViolationsResult uri: '{}'", path);
        String jsonResponse = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        LOGGER.info("getLimitViolationsResult resp: '{}'", jsonResponse);
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

    public JsonNode contingencyCount(String studyId, String rootNetworkUuid, String nodeId, String listId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}/contingency-count?contingencyListName={listId}")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId, listId)
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

    public void runSecurityAnalysis(String studyId, String rootNetworkUuid, String nodeId, String listId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}/security-analysis/run?contingencyListName={listId}")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId, listId)
                .toUriString();
        LOGGER.info("runSecurityAnalysis uri: '{}'", path);
        String jsonResponse = webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        LOGGER.info("runSecurityAnalysis resp: '{}'", jsonResponse);
    }

    public void deleteEquipment(String studyId, String nodeId, String eqptDeleteType, String equipmentId) {
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/nodes/{nodeId}/network-modifications")
                .buildAndExpand(studyId, nodeId)
                .toUriString();
        LOGGER.info("deleteEquipment uri: '{}'", path);
        // create body
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("type", "EQUIPMENT_DELETION");
        body.put("equipmentId", equipmentId);
        body.put("equipmentType", eqptDeleteType);
        webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .body(BodyInserters.fromValue(body.toString()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void modifyEquipmentAttribute(String studyId, String nodeId, String modificationType, String equipmentId, String attributeName, String attributeValue) {
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/nodes/{nodeId}/network-modifications")
                .buildAndExpand(studyId, nodeId)
                .toUriString();
        LOGGER.info("modifyEquipmentAttribute uri: '{}'", path);
        // create body
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("type", modificationType);
        body.put("equipmentId", equipmentId);
        ObjectNode attribute = mapper.createObjectNode();
        attribute.put("value", attributeValue);
        attribute.put("op", "SET");
        body.set(attributeName, attribute);
        webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .body(BodyInserters.fromValue(body.toString()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void createModification(String studyId, String nodeId, String resourceFileContent) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/network-modifications")
                .buildAndExpand(studyId, nodeId)
                .toUriString();
        LOGGER.info("createModification {}", path);
        webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .body(BodyInserters.fromValue(resourceFileContent))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void editModification(String studyId, String nodeId, String modificationId, String resourceFileContent) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/network-modifications/{modificationId}")
                .buildAndExpand(studyId, nodeId, modificationId)
                .toUriString();
        LOGGER.info("editModification {}", path);
        webClient.put()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .body(BodyInserters.fromValue(resourceFileContent))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void editModificationAttribute(String studyId, String nodeId, String modificationId, String attributeName, String attributeValue) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/network-modifications/{modificationId}")
                .buildAndExpand(studyId, nodeId, modificationId)
                .toUriString();
        LOGGER.info("editModificationAttribute {}", path);
        JsonNode modification = ModificationsRequests.getInstance().getModification(modificationId);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = modification.deepCopy();
        body.set(attributeName, mapper.convertValue(attributeValue, JsonNode.class));
        webClient.put()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .body(BodyInserters.fromValue(body.toString()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public JsonNode getModifications(String studyId, String nodeId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{nodeId}/network-modifications")
                .buildAndExpand(studyId, nodeId)
                .toUriString();
        LOGGER.info("getModifications {}", path);
        String jsonResponse = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        LOGGER.info("getModifications resp: '{}'", jsonResponse);
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

    public void duplicateModification(String studyId, String sourceNodeId, String targetNodeId, String action, String modificationId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/nodes/{targetNodeId}?action={action}&originStudyUuid={sourceStudyUuid}&originNodeUuid={sourceNodeId}")
                .buildAndExpand(studyId, targetNodeId, action, studyId, sourceNodeId)
                .toUriString();
        ObjectMapper mapper = new ObjectMapper();
        var body = mapper.createArrayNode();
        body.add(modificationId);

        LOGGER.info("duplicateModification {}", path);
        webClient.put()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public int deleteNode(String studyId, String nodeId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/tree/nodes?ids={nodeId}")
                .buildAndExpand(studyId, nodeId)
                .toUriString();
        LOGGER.info("deleteNode uri: '{}'", path);
        try {
            webClient.delete()
                    .uri(path)
                    .header("userId", EnvProperties.getInstance().getUserName())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException resp) {
            return resp.getStatusCode().value();
        }
        return 200;
    }

    public String createNode(String studyId, String nodeId, String newNodeName, String creationMode) {
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/tree/nodes/{nodeId}?mode={creationMode}")
                .buildAndExpand(studyId, nodeId, creationMode)
                .toUriString();
        LOGGER.info("createNode uri: '{}'", path);

        // create body (json tree)
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("globalBuildStatus", "NOT_BUILT");
        body.put("localBuildStatus", "NOT_BUILT");
        body.put("name", newNodeName);
        body.put("type", "NETWORK_MODIFICATION");

        String jsonResponse = webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .body(BodyInserters.fromValue(body.toString()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            if (jsonResponse != null) {
                JsonNode rootValue = mapper.readTree(jsonResponse);
                if (rootValue.has("id")) {
                    return rootValue.get("id").asText();
                }
            }
        } catch (JsonProcessingException je) {
            return null;
        }
        return null;
    }

    public String createSequence(String studyId, String nodeId, String sequenceType) {
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/tree/nodes/{nodeId}?sequenceType={sequenceType}")
                .buildAndExpand(studyId, nodeId, sequenceType)
                .toUriString();
        LOGGER.info("createSequence uri: '{}'", path);

        String jsonResponse = webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            if (jsonResponse != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootValue = mapper.readTree(jsonResponse);
                if (rootValue.has("id")) {
                    return rootValue.get("id").asText();
                }
            }
        } catch (JsonProcessingException je) {
            return null;
        }
        return null;
    }

    public void renameNode(String studyId, String nodeId, String newNodeName) {
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/tree/nodes")
                .buildAndExpand(studyId)
                .toUriString();
        LOGGER.info("renameNode uri: '{}'", path);

        // create body (json tree)
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("id", nodeId);
        body.put("name", newNodeName);
        body.put("type", "NETWORK_MODIFICATION");

        webClient.put()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
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

    public UUID exportCase(String studyId, String rootNetworkUuid, String nodeId, String format, String fileName) {
        String path = UriComponentsBuilder
                .fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}/export-network/{fmt}")
                .queryParam("fileName", fileName)
                .queryParam("formatParameters", "{}")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId, format)
                .toUriString();

        return webClient.get()
                .uri(path)
                .accept(MediaType.APPLICATION_JSON)
                .header("userId", EnvProperties.getInstance().getUserName())
                .retrieve()
                .bodyToMono(UUID.class)
                .block();
    }

    public void runSensitivityAnalysis(String studyId, String rootNetworkUuid, String nodeId) {
        String path = UriComponentsBuilder.fromPath(
                        "studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/run")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId)
                .toUriString();
        LOGGER.info("runSensitivityAnalysis uri: '{}'", path);

        webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String getSensitivityStatus(String studyId, String rootNetworkUuid, String nodeId) {
        String path = UriComponentsBuilder.fromPath("studies/{studyId}/root-networks/{rootNetworkUuid}/nodes/{nodeId}/sensitivity-analysis/status")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId)
                .toUriString();
        return webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public JsonNode getParentNodesReport(String studyId, String rootNetworkUuid, String nodeId, String reportType, boolean nodeOnlyReport) {
        String path = UriComponentsBuilder.fromPath("/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/parent-nodes-report?nodeOnlyReport={nodeOnlyReport}&reportType={reportType}&severityLevels=INFO&severityLevels=WARN&severityLevels=ERROR&severityLevels=FATAL")
                .buildAndExpand(studyId, rootNetworkUuid, nodeId, nodeOnlyReport, reportType)
                .toUriString();
        String response = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        LOGGER.info("getParentNodesReport uri: '{}'", path);
        LOGGER.info("getParentNodesReport response: '{}'", response);

        if (response != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readTree(response);
            } catch (JsonProcessingException je) {
                return null;
            }
        }
        return null;
    }
}

