/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.explore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.gridsuite.bddtests.common.EnvProperties;
import org.gridsuite.bddtests.directory.DirectoryElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

import java.nio.file.Path;

public final class ExploreRequests {

    public static synchronized ExploreRequests getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ExploreRequests();
        }
        return INSTANCE;
    }

    private static ExploreRequests INSTANCE = null;
    private final WebClient webClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(ExploreRequests.class);

    private ExploreRequests() {
        webClient = EnvProperties.getInstance().getWebClient(EnvProperties.MicroService.EXPLORE_SERVER);
    }

    public void createStudyFromCase(String studyName, String caseId, String description, String directoryId, String userId,
                                    String caseFormat, String paramsAsRequestBody, boolean duplicateCase) {
        String path = UriComponentsBuilder.fromPath(
                        "explore/studies/{studyName}/cases/{caseUuid}?duplicateCase={duplicateCase}&description={description}&parentDirectoryUuid={parentDirectoryUuid}&caseFormat={caseFormat}")
                .buildAndExpand(studyName, caseId, duplicateCase, description, directoryId, caseFormat)
                .toUriString();
        LOGGER.info("createStudyFromCase uri: '{}'", path);

        if (paramsAsRequestBody != null) {
            webClient.post()
                    .uri(path)
                    .header("userId", userId)
                    .body(BodyInserters.fromValue(paramsAsRequestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } else {
            webClient.post()
                    .uri(path)
                    .header("userId", userId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        }
    }

    public void createCaseFromFile(String caseName, Path filePath, String description, String directoryId, String userId) {
        String path = UriComponentsBuilder.fromPath(
                        "explore/cases/{caseName}?description={description}&parentDirectoryUuid={parentDirectoryUuid}")
                .buildAndExpand(caseName, description, directoryId)
                .toUriString();
        LOGGER.info("createCaseFromFile uri: '{}'", path);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        Resource caseResource = new FileSystemResource(filePath);
        builder.part("caseFile", caseResource);

        webClient.post()
                .uri(path)
                .header("userId", userId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void removeElement(String eltId, String userId) {
        // remove a single element or a whole directory (RECURSIVELY)
        String path = UriComponentsBuilder.fromPath("explore/elements/{elementUuid}")
                .buildAndExpand(eltId)
                .toUriString();

        webClient.delete()
                .uri(path)
                .header("userId", userId)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

        public JsonNode getImportParameters(String caseId) {
        String path = UriComponentsBuilder.fromPath(
                        "explore/cases/{caseId}/import-parameters")
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

    public String createDirectory(String dirName, String parentId, String owner) {
        // create body (json tree)
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("elementName", dirName);
        body.put("owner", owner);
        body.put("type", "DIRECTORY");
        body.putNull("elementUuid");

        String jsonResponse = webClient.post()
            .uri("explore/directories/" + parentId + "/directories")
            .header("userId", owner)
            .body(BodyInserters.fromValue(body.toString()))
            .retrieve()
            .bodyToMono(String.class)
            .block();
        try {
            if (jsonResponse != null) {
                JsonNode rootValue = mapper.readTree(jsonResponse);
                if (rootValue.has("elementUuid")) {
                    return rootValue.get("elementUuid").asText();
                }
            }
        } catch (JsonProcessingException je) {
            return null;
        }
        return null;
    }

    public String createRootDirectory(String dirName, String user, String desc) {
        // create body (json tree)
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("elementName", dirName);
        body.put("owner", user);
        body.put("description", desc);

        String jsonResponse = webClient.post()
            .uri("explore/directories/root-directories")
            .header("userId", user)
            .body(BodyInserters.fromValue(body.toString()))
            .retrieve()
            .bodyToMono(String.class)
            .block();
        try {
            if (jsonResponse != null) {
                JsonNode rootValue = mapper.readTree(jsonResponse);
                if (rootValue.has("elementUuid")) {
                    return rootValue.get("elementUuid").asText();
                }
            }
        } catch (JsonProcessingException je) {
            return null;
        }
        return null;
    }

    public String getElementId(String userId, String directoryId, String elementType, String elementName) {
        final String[] eltId = {null};

        // iterate through the stream
        webClient.get()
            .uri("explore/directories/" + directoryId + "/elements")
            .header("userId", userId)
            .retrieve()
            .bodyToFlux(DirectoryElement.class)
            .doOnNext(elt -> LOGGER.info("getElementId '{}'", elt))
            .takeUntil(elt -> {
                    if (elt.getElementName().equalsIgnoreCase(elementName)
                        && elt.getType().equalsIgnoreCase(elementType)) {
                        eltId[0] = elt.getElementUuid();
                        return true;    // exit condition (flux disposal)
                    } else {
                        return false;
                    }
                }
            )
            .blockLast(); // this is a blocking subscribe
        return eltId[0];
    }

    private Flux<DirectoryElement> requestRootDirectory(String userId) {
        return webClient.get()
            .uri("explore/directories/root-directories")
            .header("userId", userId)
            .retrieve()
            .bodyToFlux(DirectoryElement.class);
    }

    public String getRootDirectoryId(String userId, String directoryName) {
        final String[] dirId = {null};

        // iterate through the stream
        ExploreRequests.getInstance().requestRootDirectory(userId).doOnNext(
                dir -> LOGGER.info("getRootDirectoryId '{}'", dir)
            )
            .takeUntil(dir -> {
                    if (dir.getElementName().equalsIgnoreCase(directoryName)) {
                        dirId[0] = dir.getElementUuid();
                        return true;    // exit condition (flux disposal)
                    } else {
                        return false;
                    }
                }
            )
            .blockLast(); // this is a blocking subscribe
        return dirId[0];
    }
}
