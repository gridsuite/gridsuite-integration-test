package org.gridsuite.bddtests.networkconversion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.bddtests.common.EnvProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.UUID;

import static java.nio.file.StandardOpenOption.CREATE;

public final class NetworkConversionRequests {

    public static synchronized NetworkConversionRequests getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new NetworkConversionRequests();
        }
        return INSTANCE;
    }

    private static NetworkConversionRequests INSTANCE = null;
    private final WebClient webClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkConversionRequests.class);

    private NetworkConversionRequests() {
        webClient = EnvProperties.getInstance().getWebClient(EnvProperties.MicroService.NETWORK_CONVERSION_SERVER);
    }

    public JsonNode getImportParameters(String caseId) {
        String path = UriComponentsBuilder.fromPath(
                        "cases/{caseId}/import-parameters")
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

    public void downloadExportFile(UUID exportUuid, Path filePath) {
        String path = UriComponentsBuilder
                .fromPath("download-file/{exportUuid}")
                .buildAndExpand(exportUuid)
                .toUriString();

        Flux<DataBuffer> result = webClient.get()
                .uri(path)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .bodyToFlux(DataBuffer.class);

        LOGGER.info("Downloading exported file {} into {} ...", exportUuid, filePath);
        DataBufferUtils.write(result, filePath, CREATE).block();
    }
}
