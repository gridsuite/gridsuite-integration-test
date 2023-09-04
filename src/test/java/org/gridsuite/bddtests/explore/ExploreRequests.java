/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.explore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.gridsuite.bddtests.common.EnvProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

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
    private final String version = "v1";

    private static final Logger LOGGER = LoggerFactory.getLogger(ExploreRequests.class);

    private ExploreRequests() {
        webClient = EnvProperties.getInstance().getWebClient(EnvProperties.MicroService.ExploreServer);
    }

    public void createStudyFromCase(String studyName, String caseId, String description, String directoryId, String userId) {
        String path = UriComponentsBuilder.fromPath(
            "explore/studies/{studyName}/cases/{caseUuid}?duplicateCase=true&description={description}&parentDirectoryUuid={parentDirectoryUuid}")
            .buildAndExpand(studyName, caseId, description, directoryId)
            .toUriString();
        LOGGER.info("createStudyFromCase uri: '{}'", path);

        webClient.post()
                .uri(path)
                .header("userId", userId)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void createStudyFromFile(String studyName, Path filePath, String description, String directoryId, String userId) {
        String path = UriComponentsBuilder.fromPath(
                        "explore/studies/{studyName}?description={description}&parentDirectoryUuid={parentDirectoryUuid}")
                .buildAndExpand(studyName, description, directoryId)
                .toUriString();
        LOGGER.info("createStudyFromFile uri: '{}'", path);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        Resource caseResource = new FileSystemResource(filePath);
        builder.part("caseFile", caseResource);

        String response = webClient.post()
                .uri(path)
                .header("userId", userId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
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

        String response = webClient.post()
                .uri(path)
                .header("userId", userId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void createFormContingencyList(String listName, String description, String directoryId, String userId) {
        String path = UriComponentsBuilder.fromPath(
                        "explore/form-contingency-lists/{listName}?description={description}&parentDirectoryUuid={parentDirectoryUuid}")
                .buildAndExpand(listName, description, directoryId)
                .toUriString();
        LOGGER.info("createFormContingencyList uri: '{}'", path);

        // create a default body (json tree)
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("equipmentID", "*");
        body.put("equipmentName", "*");
        body.put("equipmentType", "LINE");
        body.put("nominalVoltage", -1);
        body.put("nominalVoltageOperator", "=");

        webClient.post()
                .uri(path)
                .header("userId", userId)
                .body(BodyInserters.fromValue(body.toString()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void copyFormContingencyListAsScript(String listId, String newScriptListName, String dirId, String userId) {
        String path = UriComponentsBuilder.fromPath(
                        "explore/form-contingency-lists/{listId}/new-script/{scriptName}?parentDirectoryUuid={parentDirectoryUuid}")
                .buildAndExpand(listId, newScriptListName, dirId)
                .toUriString();
        LOGGER.info("copyFormContingencyListAsScript uri: '{}'", path);

        webClient.post()
                .uri(path)
                .header("userId", userId)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void createDefaultFilter(String elementName, String description, String parentDirId, String userId, String filterType) {
        String path = UriComponentsBuilder.fromPath(
                        "explore/filters?name={filterName}&description={description}&parentDirectoryUuid={parentDirectoryUuid}")
                .buildAndExpand(elementName, description, parentDirId)
                .toUriString();
        LOGGER.info("createDefaultFilter uri: '{}'", path);

        // create a default body (json tree)
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode eqptFilter = mapper.createObjectNode();
        eqptFilter.put("equipmentType", filterType.equalsIgnoreCase("SCRIPT") ? null : "LINE");
        ObjectNode body = mapper.createObjectNode();
        body.put("transient", "true");
        body.put("type", filterType);
        body.set("equipmentFilterForm", eqptFilter);

        webClient.post()
                .uri(path)
                .header("userId", userId)
                .body(BodyInserters.fromValue(body.toString()))
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
}
