package org.gridsuite.bddtests.explore;

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
import java.util.Optional;

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

    public void duplicateStudy(String studyId, String parentDirectoryId) {
        String path = UriComponentsBuilder.fromPath("explore/studies")
                .queryParam("duplicateFrom", studyId)
                .queryParamIfPresent("parentDirectoryUuid", Optional.ofNullable(parentDirectoryId))
                .toUriString();
        LOGGER.info("duplicateStudy uri: '{}'", path);
        webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
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

        webClient.post()
                .uri(path)
                .header("userId", userId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void createContingencyList(String listName, String description, String directoryId, String jsonBody, String listType) {
        // listType = form / identifier
        String path = UriComponentsBuilder.fromPath(
                        "explore/{listType}-contingency-lists/{listName}?description={description}&parentDirectoryUuid={parentDirectoryUuid}")
                .buildAndExpand(listType, listName, description, directoryId)
                .toUriString();
        LOGGER.info("createContingencyList uri: '{}'", path);
        webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .body(BodyInserters.fromValue(jsonBody))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void createFormFilter(String filterName, String description, String directoryId, String jsonBody) {
        String path = UriComponentsBuilder.fromPath(
                        "explore/filters?name={filterName}&description={description}&parentDirectoryUuid={parentDirectoryUuid}")
                .buildAndExpand(filterName, description, directoryId)
                .toUriString();
        LOGGER.info("createFormFilter uri: '{}'", path);

        webClient.post()
                .uri(path)
                .header("userId", EnvProperties.getInstance().getUserName())
                .body(BodyInserters.fromValue(jsonBody))
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
