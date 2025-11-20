package org.gridsuite.bddtests.directory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.gridsuite.bddtests.common.EnvProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

import java.util.List;

public final class DirectoryRequests {

    public static synchronized DirectoryRequests getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DirectoryRequests();
        }
        return INSTANCE;
    }

    private static DirectoryRequests INSTANCE = null;
    private final WebClient webClient;
    private final String version = "v1";

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryRequests.class);

    private DirectoryRequests() {
        webClient = EnvProperties.getInstance().getWebClient(EnvProperties.MicroService.DIRECTORY_SERVER);
    }

    private Flux<DirectoryElement> requestRootDirectory(String userId) {
        return webClient.get()
                .uri("root-directories")
                .header("userId", userId)
                .retrieve()
                .bodyToFlux(DirectoryElement.class);
    }

    public JsonNode getElements(String userId, String directoryId) {
        String jsonResponse = webClient.get()
                .uri("directories/" + directoryId + "/elements")
                .header("userId", userId)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        LOGGER.info("getElements resp: '{}'", jsonResponse);
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

    public String getRootDirectoryId(String userId, String directoryName) {
        final String[] dirId = {null};

        // iterate through the stream
        DirectoryRequests.getInstance().requestRootDirectory(userId).doOnNext(
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

    public String getElementId(String userId, String directoryId, String elementType, String elementName) {
        final String[] eltId = {null};

        // iterate through the stream
        webClient.get()
                .uri("directories/" + directoryId + "/elements")
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

    public String createRootDirectory(String dirName, String user, String desc) {
        // create body (json tree)
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("elementName", dirName);
        body.put("owner", user);
        body.put("description", desc);

        String jsonResponse = webClient.post()
                .uri("root-directories")
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

    public JsonNode getElementInfo(String elementId) {
        String path = UriComponentsBuilder.fromPath("elements/{eltId}")
                .buildAndExpand(elementId)
                .toUriString();
        LOGGER.info("getElementInfo uri: '{}'", path);

        String response = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToFlux(String.class)
                .blockLast(); // this is a blocking subscribe
        LOGGER.info("getElementInfo data= '{}'", response);

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

    public void renameElement(String elementId, String newName, String owner) {
        String path = UriComponentsBuilder.fromPath("elements/{eltId}")
                .buildAndExpand(elementId)
                .toUriString();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("elementName", newName);

        webClient.put()
                .uri(path)
                .header("userId", owner)
                .body(BodyInserters.fromValue(body.toString()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
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
                .uri("directories/" + parentId + "/elements")
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

    public int moveElement(List<String> elementId, String dirId, String owner) {
        String path = UriComponentsBuilder.fromPath("elements?targetDirectoryUuid={targetDirId}")
                .buildAndExpand(dirId)
                .toUriString();
        LOGGER.info("moveElement path= '{}'", path);
        try {
            webClient.put()
                    .uri(path)
                    .header("userId", owner)
                    .bodyValue(elementId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException resp) {
            return resp.getStatusCode().value();
        }
        return 200;
    }
}
