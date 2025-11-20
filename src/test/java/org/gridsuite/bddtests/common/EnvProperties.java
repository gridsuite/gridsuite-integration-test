package org.gridsuite.bddtests.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;

public final class EnvProperties {

    public static synchronized EnvProperties getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EnvProperties();
        }
        return INSTANCE;
    }

    public String getTmpRootDir() {
        return tmpRootDir;
    }

    public void setTmpRootDir(String tmpSubDir) {
        this.tmpRootDir = tmpSubDir;
    }

    public enum MicroService {
        ACTION_SERVER,
        CASE_SERVER,
        CONFIG_SERVER,
        DIRECTORY_NOTIFICATION_SERVER,
        DIRECTORY_SERVER,
        EXPLORE_SERVER,
        FILTER_SERVER,
        MODIFICATION_SERVER,
        NETWORK_CONVERSION_SERVER,
        STUDY_NOTIFICATION_SERVER,
        STUDY_SERVER
    }

    private static EnvProperties INSTANCE = null;
    private final String version = "v1";  // TODO only v1 for now
    private Properties props = null;
    private String userName = null;
    private String token = null;
    private String tmpRootDir = "root_bdd";
    private final EnumMap<MicroService, String> msUrlMap = new EnumMap<>(MicroService.class);

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvProperties.class);

    private EnvProperties() {
    }

    public String getMicroServiceUrl(MicroService ms) {
        return msUrlMap.get(ms);
    }

    public Properties getProps() {
        return props;
    }

    public String getUserName() {
        return userName != null ? userName : props.getProperty("username");
    }

    public String getToken() {
        return token;
    }

    public String getHost() {
        return props.getProperty("api_hostname");
    }

    public String getWsHost() {
        return props.getProperty("ws_hostname");
    }

    public String getBearer() {
        String bearerSystemProp = System.getProperty("using_bearer"); // from command-line
        if (bearerSystemProp != null) {
            return bearerSystemProp;
        }
        String propValue = props.getProperty("bearer", null); // from property file
        if (propValue != null && propValue.isEmpty()) {
            propValue = null;
        }
        return propValue;
    }

    public String getProp(String name) {
        String propValue = props.getProperty(name, null);
        if (propValue != null && propValue.isEmpty()) {
            propValue = null;
        }
        return propValue;
    }

    public String getClientId() {
        return getProp("client_id");
    }

    public String getTokenMode() {
        return getProp("token_mode");
    }

    public boolean useToken() {
        String tokenMode = getTokenMode();
        return tokenMode != null && ("jwt".equalsIgnoreCase(tokenMode) || "gaia".equalsIgnoreCase(tokenMode));
    }

    public String getClientSecret() {
        return getProp("client_secret");
    }

    public String getAuthUrl() {
        return getProp("auth_url");
    }

    public String getUserFromBearer(String bearer) {
        // check token is a valid JWT bearer, and extract username ("sub") from it
        if (bearer != null) {
            try {
                JWT jwt = JWTParser.parse(bearer);
                Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();
                Object sub = claims.getOrDefault("sub", null);
                if (sub instanceof String) {
                    return (String) sub;
                }
            } catch (ParseException e) {
                return null;
            }
        }
        return null;
    }

    public String getAccessToken() {
        String clientId = getClientId();
        String clientSecret = getClientSecret();
        String authUrl = getAuthUrl();
        if (clientId == null || clientSecret == null || authUrl == null) {
            return null;
        }
        // curl equiv : curl -XPOST 'https://gaia-sso.opf.rte-france.com/as/token.oauth2' -d grant_type=client_credentials -dclient_id=iii -d client_secret=sss
        WebClient authClient = WebClient.builder()
                .baseUrl(authUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();
        String response = null;
        try {
            response = authClient.post()
                    .uri("/as/token.oauth2")
                    .body(BodyInserters.fromValue("grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            LOGGER.info("getAccessToken response: '{}'", response);
        } catch (Exception e) {
            LOGGER.error("getAccessToken error: '{}'", e.getMessage());
        }
        if (response != null) {
            try {
                // return ex: '{"access_token":"4sej3K5bGmSM38zu2VBgffwBv5P1","token_type":"Bearer","expires_in":7199}'
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(response);
                if (node != null && node.has("access_token")) {
                    return node.get("access_token").asText();
                }
            } catch (JsonProcessingException je) {
                return null;
            }
        }
        return null;
    }

    public boolean init(String environmentName) {
        boolean good = false;
        String propsFileName = environmentName + "_env.properties";
        try (InputStream input = EnvProperties.class.getClassLoader().getResourceAsStream(propsFileName)) {
            if (input == null) {
                LOGGER.warn("no Property file called '{}'", propsFileName);
            } else {
                LOGGER.info("Loading Property file '{}'", propsFileName);
                Properties newProps = new Properties();
                newProps.load(input);
                props = newProps;
                String host = getHost();
                assertNotNull("Cannot find api_hostname property", host);
                String wsHost = getWsHost();
                assertNotNull("Cannot find ws_hostname property", wsHost);

                if (!useToken()) {
                    LOGGER.info("No Bearer used, username property = {}", getUserName());
                    msUrlMap.put(MicroService.ACTION_SERVER, host + ":5022");
                    msUrlMap.put(MicroService.CASE_SERVER, host + ":5000");
                    msUrlMap.put(MicroService.CONFIG_SERVER, host + ":5025");
                    msUrlMap.put(MicroService.DIRECTORY_NOTIFICATION_SERVER, wsHost + ":5004");
                    msUrlMap.put(MicroService.DIRECTORY_SERVER, host + ":5026");
                    msUrlMap.put(MicroService.EXPLORE_SERVER, host + ":5029");
                    msUrlMap.put(MicroService.FILTER_SERVER, host + ":5027");
                    msUrlMap.put(MicroService.MODIFICATION_SERVER, host + ":5007");
                    msUrlMap.put(MicroService.NETWORK_CONVERSION_SERVER, host + ":5003");
                    msUrlMap.put(MicroService.STUDY_NOTIFICATION_SERVER, wsHost + ":5009");
                    msUrlMap.put(MicroService.STUDY_SERVER, host + ":5001");
                } else {
                    if ("jwt" .equalsIgnoreCase(getTokenMode())) {
                        String bearer = getBearer();
                        userName = getUserFromBearer(bearer);
                        assertNotNull("Wrong JWT bearer/token, cannot extract username from it", userName);
                        token = bearer;
                        LOGGER.info("Using JWT Bearer, username from token = {}", getUserName());
                    } else {
                        token = getAccessToken();
                        assertNotNull("Cannot get access token from '" + getAuthUrl() + "' for user '" + getClientId() + "'", token);
                        userName = getClientId();
                        LOGGER.info("Using access token Bearer, username = {}", getClientId());
                    }
                    msUrlMap.put(MicroService.ACTION_SERVER, host + "/actions");
                    msUrlMap.put(MicroService.CASE_SERVER, host + "/case");
                    msUrlMap.put(MicroService.CONFIG_SERVER, host + "/config");
                    msUrlMap.put(MicroService.DIRECTORY_NOTIFICATION_SERVER, wsHost + "/directory-notification");
                    msUrlMap.put(MicroService.DIRECTORY_SERVER, host + "/directory");
                    msUrlMap.put(MicroService.EXPLORE_SERVER, host + "/explore");
                    msUrlMap.put(MicroService.FILTER_SERVER, host + "/filter");
                    msUrlMap.put(MicroService.MODIFICATION_SERVER, host + "/network-modification");
                    msUrlMap.put(MicroService.NETWORK_CONVERSION_SERVER, host + "/network-conversion");
                    msUrlMap.put(MicroService.STUDY_NOTIFICATION_SERVER, wsHost + "/study-notification");
                    msUrlMap.put(MicroService.STUDY_SERVER, host + "/study");
                }
                good = true;
            }
        } catch (IOException ex) {
            props = null;
        }
        return good;
    }

    public WebClient getWebClient(MicroService ms) {
        String serverUrl = getMicroServiceUrl(ms);

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(serverUrl + "/" + version + "/")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // to avoid DataBufferLimitException while receiving heavy response
                .codecs(codecs -> codecs
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024));

        String localToken = getToken();
        if (localToken == null) {
            LOGGER.info("getWebClient '{}'", serverUrl);
        } else {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + localToken);
            LOGGER.info("getWebClient with bearer '{}'", serverUrl);
        }
        return builder.build();
    }
}

