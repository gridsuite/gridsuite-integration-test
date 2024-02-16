/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.common;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    public enum MicroService {
        CaseServer, DirectoryServer, ExploreServer, StudyServer, ActionServer
    }

    private static EnvProperties INSTANCE = null;
    private String envName = null;
    private Properties props = null;
    private String userName = null;
    private final EnumMap<MicroService, String> msUrlMap = new EnumMap<>(MicroService.class);

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvProperties.class);

    private EnvProperties() {
    }

    public String getMicroServiceUrl(MicroService ms) {
        return msUrlMap.get(ms);
    }

    public boolean isInit() {
        return props != null;
    }

    public Properties getProps() {
        return props;
    }

    public String getEnvName() {
        return envName;
    }

    public String getUserName() {
        return userName != null ? userName : props.getProperty("username");
    }

    public String getHost() {
        return props.getProperty("hostname");
    }

    public String getBearer() {
        String propValue = props.getProperty("bearer", null);
        if (propValue != null && propValue.isEmpty()) {
            propValue = null;
        }
        return propValue;
    }

    public String getExploreUrl() {
        return props.getProperty("explore.url");
    }

    public boolean init(String environmentName) {
        boolean good = false;

        // property file for a given env configuration
        String propsFileName = environmentName + "_env.properties";
        try (InputStream input = EnvProperties.class.getClassLoader().getResourceAsStream(propsFileName)) {
            if (input == null) {
                LOGGER.warn("no Property file called '{}'", propsFileName);
            } else {
                LOGGER.info("Loading Property file '{}'", propsFileName);
                Properties newProps = new Properties();
                newProps.load(input);

                envName = environmentName;
                props = newProps;

                String host = getHost();
                assertNotNull("Cannot find hostname property", host);
                String bearer = getBearer();
                // a bearer means the host is a gateway
                if (bearer != null) {
                    // check token is valid, and extract username from it
                    try {
                        JWT jwt = JWTParser.parse(bearer);
                        Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();
                        Object sub = claims.getOrDefault("sub", null);
                        if (sub instanceof String) {
                            userName = (String) sub;
                        }
                    } catch (ParseException e) {
                        userName = null;
                    }
                    assertNotNull("Wrong bearer/token, cannot extract username from it", userName);
                    LOGGER.info("Using Bearer, username from token = " + this.getUserName());
                    msUrlMap.put(MicroService.CaseServer, host + "/case");
                    msUrlMap.put(MicroService.StudyServer, host + "/study");
                    msUrlMap.put(MicroService.DirectoryServer, host + "/directory");
                    msUrlMap.put(MicroService.ExploreServer, host + "/explore");
                    msUrlMap.put(MicroService.ActionServer, host + "/actions");
                } else {
                    LOGGER.info("No Bearer, username property = " + this.getUserName());
                    msUrlMap.put(MicroService.CaseServer, host + ":5000");
                    msUrlMap.put(MicroService.StudyServer, host + ":5001");
                    msUrlMap.put(MicroService.DirectoryServer, host + ":5026");
                    msUrlMap.put(MicroService.ExploreServer, host + ":5029");
                    msUrlMap.put(MicroService.ActionServer, host + ":5022");
                }
                good = true;
            }
        } catch (IOException ex) {
            envName = null;
            props = null;
        }
        return good;
    }

    public WebClient getWebClient(MicroService ms) {
        String serverUrl = getMicroServiceUrl(ms);

        String version = "v1";
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(serverUrl + "/" + version + "/")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        String bearer = getBearer();
        if (bearer == null) {
            LOGGER.info("getWebClient '{}'", serverUrl);
        } else {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
            LOGGER.info("getWebClient with bearer '{}'", serverUrl);
        }
        return builder.build();
    }
}

