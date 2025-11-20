package org.gridsuite.bddtests.cases;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import reactor.core.publisher.Flux;

import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;

public final class CaseRequests {

    public static synchronized CaseRequests getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CaseRequests();
        }
        return INSTANCE;
    }

    private static CaseRequests INSTANCE = null;
    private WebClient webClient;
    private final String version = "v1";

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseRequests.class);

    private CaseRequests() {
        webClient = EnvProperties.getInstance().getWebClient(EnvProperties.MicroService.CASE_SERVER);
    }

    private Flux<CaseElement> requestAllCases() {
        return webClient.get()
                .uri("cases")
                .retrieve()
                .bodyToFlux(CaseElement.class);
    }

    public String getCaseId(String caseName) {
        final String[] caseId = {null};

        // iterate through the stream
        CaseRequests.getInstance().requestAllCases().doOnNext(
                        thecase -> {
                            LOGGER.info("getCaseId '{}'", thecase.toString());
                        })
                .takeUntil(
                        thecase -> {
                            if (thecase.getCaseName().equalsIgnoreCase(caseName)) {
                                caseId[0] = thecase.getCaseUuid();
                                return true;    // exit condition (flux disposal)
                            } else {
                                return false;
                            }
                        }
                )
                .blockLast(); // this is a blocking subscribe

        return caseId[0];
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
                            LOGGER.info("existsCase '{}'", s.toString());
                            exists[0] = s.equalsIgnoreCase("true");
                        }
                ).block();

        return exists[0];
    }

    public String caseFormat(String caseId) {
        String path = UriComponentsBuilder.fromPath("cases/metadata?ids={caseId}")
                .buildAndExpand(caseId)
                .toUriString();
        String jsonResponse = webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        LOGGER.info("caseFormat resp: '{}'", jsonResponse);
        try {
            if (jsonResponse != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode metadataArray = mapper.readTree(jsonResponse);
                if (metadataArray.isArray() && metadataArray.size() == 1) {
                    JsonNode metadata = metadataArray.get(0);
                    return metadata.get("format").asText();
                }
            }
        } catch (JsonProcessingException je) {
            return null;
        }
        return null;
    }

    public String createCaseFromFile(Path filePath, String userId) {
        String path = UriComponentsBuilder.fromPath("cases").toUriString();
        LOGGER.info("createCaseFromFile uri: '{}'", path);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        Resource caseResource = new FileSystemResource(filePath);
        builder.part("file", caseResource);

        String caseId = webClient.post()
                .uri(path)
                .header("userId", userId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertNotNull("Case not created", caseId);
        if (caseId.startsWith("\"")) {
            caseId = caseId.substring(1, caseId.length() - 1);
        }
        return caseId;
    }
}
