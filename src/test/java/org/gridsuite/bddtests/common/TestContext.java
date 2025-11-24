/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.common;

import com.fasterxml.jackson.databind.JsonNode;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import org.gridsuite.bddtests.directory.DirectoryRequests;
import org.gridsuite.bddtests.explore.ExploreRequests;
import org.gridsuite.bddtests.study.StudyRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestContext {

    public enum Computation {
        LOADFLOW,
    }

    public static class Node {
        public Node(String nId, String sId) {
            nodeId = nId;
            studyId = sId;
        }

        public String nodeId;
        public String studyId;
    }

    public static class RootNetwork {
        public RootNetwork(String rootNetworkUuid) {
            this.rootNetworkUuid = rootNetworkUuid;
        }

        public String rootNetworkUuid;
    }

    // set of element alias/name and their uuid, we want to use/memorise during a whole scenario, between the steps
    private Map<String, String> currentDirectoryIds;
    private Map<String, String> currentStudyIds;
    private Map<String, String> currentCaseIds;
    private Map<String, Node> currentNodeIds;
    private Map<String, RootNetwork> currentRootNetworkIds;
    private Map<String, Integer> configIntParameters;
    private Map<String, JsonNode> currentCaseExtensions;
    // a tmp dir uuid possibly associated to a scenario
    private String tmpRootDirId = null;

    // CONSTANTS:
    public static final ArrayList<String> LOADFLOW_PROVIDERS = new ArrayList<>(List.of("OpenLoadFlow"));
    public static final int MAX_WAITING_TIME_IN_SEC = 180;
    public static final int MAX_COMPUTATION_WAITING_TIME_IN_SEC = 300;
    public static final String CURRENT_ELEMENT = "current";

    // extensions
    public static final Map<String, String> EXTENTION_KEYS = Map.ofEntries(
            Map.entry("CGMES", ""),
            Map.entry("XIIDM", "iidm.import.xml.included.extensions")
    );

    public static final Logger LOGGER = LoggerFactory.getLogger(TestContext.class);

    public void init() {
        LOGGER.info("BaseStepDefinitions init");
        currentStudyIds = new HashMap<>();
        currentDirectoryIds = new HashMap<>();
        currentCaseIds = new HashMap<>();
        currentNodeIds = new HashMap<>();
        currentRootNetworkIds = new HashMap<>();
        tmpRootDirId = null;
        configIntParameters = new HashMap<>();
        currentCaseExtensions = new HashMap<>();
    }

    public void reset() {
        LOGGER.info("BaseStepDefinitions reset");
        // Remove the whole tmp dir, if used by the scenario
        if (tmpRootDirId != null) {
            LOGGER.info("Remove current tmp root dir");
            String user = EnvProperties.getInstance().getUserName();
            ExploreRequests.getInstance().removeElement(tmpRootDirId, user);
        }
        currentStudyIds = null;
        currentDirectoryIds = null;
        currentCaseIds = null;
        currentNodeIds = null;
        currentRootNetworkIds = null;
    }

    // --------------------------------------------------------
    public String getDirId(String name) {
        String id = currentDirectoryIds.getOrDefault(name, null);
        assertNotNull(id, "no directory " + name);
        return id;
    }

    public String getCaseId(String name) {
        String id = currentCaseIds.getOrDefault(name, null);
        assertNotNull(id, "no case " + name);
        return id;
    }

    public String getStudyId(String name) {
        String id = currentStudyIds.getOrDefault(name, null);
        assertNotNull(id, "no study " + name);
        return id;
    }

    public JsonNode getCaseExtentions(String name) {
        JsonNode id = currentCaseExtensions.getOrDefault(name, null);
        assertNotNull(id, "no case extension data " + name);
        return id;
    }

    public Node getNodeId(String name) {
        Node id = currentNodeIds.getOrDefault(name, null);
        assertNotNull(id, "no node " + name);
        return id;
    }

    public RootNetwork getCurrentRootNetwork() {
        return currentRootNetworkIds.get(CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    public void setCurrentStudy(String aliasName, String uuid) {
        currentStudyIds.put(CURRENT_ELEMENT, uuid);
        currentStudyIds.put(aliasName, uuid);
    }

    public void setCurrentDirectory(String aliasName, String uuid) {
        currentDirectoryIds.put(CURRENT_ELEMENT, uuid);
        currentDirectoryIds.put(aliasName, uuid);
    }

    public void setCurrentCase(String aliasName, String uuid) {
        currentCaseIds.put(CURRENT_ELEMENT, uuid);
        currentCaseIds.put(aliasName, uuid);
    }

    public void setCurrentNode(String aliasName, String uuid, String studyId) {
        currentNodeIds.put(CURRENT_ELEMENT, new Node(uuid, studyId));
        currentNodeIds.put(aliasName, new Node(uuid, studyId));
    }

    public void setCurrentRootNetworkUuid(String uuid) {
        currentRootNetworkIds.put(CURRENT_ELEMENT, new RootNetwork(uuid));
    }

    public void setCaseExtentions(String aliasName, JsonNode data) {
        currentCaseExtensions.put(aliasName, data);
    }

    public String getExtensionKey(String caseType) {
        assertTrue(EXTENTION_KEYS.containsKey(caseType), "ExtensionKey must be in " + EXTENTION_KEYS.keySet());
        return EXTENTION_KEYS.get(caseType);
    }

    // --------------------------------------------------------
    public void createTmpDirectoryAs(String aliasName, String owner, boolean noRemove) {
        createTmpDirectoryAs("bddtmp_", aliasName, owner, noRemove);
    }

    // --------------------------------------------------------
    public void createTmpDirectoryAs(String tmpDirPrefix, String aliasName, String owner, boolean noRemove) {
        String dirName = tmpDirPrefix + UUID.randomUUID();
        String rootDirId = checkOrCreateRootDirectory(EnvProperties.getInstance().getTmpRootDir());
        LOGGER.info("Creating scenario temporary dir '{}' in '{}'", dirName, EnvProperties.getInstance().getTmpRootDir());
        String dirId = createDirectoryFromId(aliasName, dirName, rootDirId, owner);
        if (!noRemove) {
            tmpRootDirId = dirId;
        }
    }

    // --------------------------------------------------------
    public String checkOrCreateRootDirectory(String directoryName) {
        String userName = EnvProperties.getInstance().getUserName();
        String dirId = DirectoryRequests.getInstance().getRootDirectoryId(userName, directoryName);
        if (dirId == null) {
            dirId = createRootDirectory(directoryName, directoryName, "", userName);
        } else {
            setCurrentDirectory(directoryName, dirId);
        }
        return dirId;
    }

    // --------------------------------------------------------
    public String createRootDirectory(String dirName, String aliasName, String desc, String owner) {
        String rootDirId = DirectoryRequests.getInstance().createRootDirectory(dirName, owner, desc);
        assertNotNull(rootDirId, "Could not create root directory " + dirName);
        setCurrentDirectory(aliasName, rootDirId);
        return rootDirId;
    }


    // --------------------------------------------------------
    public String createDirectoryFromId(String aliasName, String dirName, String parentId, String owner) {
        String dirId = DirectoryRequests.getInstance().createDirectory(dirName, parentId, owner);
        assertNotNull(dirId, "Could not create directory " + dirName + " in parentId " + parentId);
        setCurrentDirectory(aliasName, dirId);
        return dirId;
    }

    // --------------------------------------------------------
    public String waitForElementCreation(String dirId, String elementType, String elementName) {
        // check element creation in target directory, and return its uuid
        RetryPolicy<String> retryPolicyDirectory = RetryPolicy.<String>builder()
                .withDelay(Duration.ofMillis(1000))
                .withMaxRetries(MAX_WAITING_TIME_IN_SEC)
                .onRetriesExceeded(e -> LOGGER.warn("Waiting time exceeded"))
                .handleResult(null)
                .build();
        LOGGER.info("Wait for '{}' {} element creation in directory (max: {} sec)", elementName, elementType, retryPolicyDirectory.getConfig().getMaxRetries());
        String user = EnvProperties.getInstance().getUserName();
        return Failsafe.with(retryPolicyDirectory).get(() -> DirectoryRequests.getInstance().getElementId(user, dirId, elementType, elementName));
    }

    public void executeAndWaitForStudyCreation(Runnable asyncRequest, String studyName, String directoryName, int secondsTimeout) {
        String dirId = getDirId(directoryName);
        NotificationWaiter.executeAndWaitForStudyCreation(asyncRequest, studyName, dirId, secondsTimeout);
    }

    // --------------------------------------------------------
    public String getElementFrom(String eltName, String eltType, String directoryName) {
        String dirId = getDirId(directoryName);
        String user = EnvProperties.getInstance().getUserName();
        String eltId = DirectoryRequests.getInstance().getElementId(user, dirId, eltType, eltName);
        assertNotNull(eltId, "Cannot find " + eltType + " named " + eltName + " in directory " + directoryName);
        return eltId;
    }

    // --------------------------------------------------------
    private boolean statusMatching(String expectedStatus, String studyId, String rootNetworkUuid, String nodeId, Computation compName) {
        if (Objects.requireNonNull(compName) == Computation.LOADFLOW) {
            return expectedStatus.equalsIgnoreCase(StudyRequests.getInstance().getLoadFlowInfos(studyId, rootNetworkUuid, nodeId));
        } else {
            assertTrue(true, "bad computation name");
        }
        return false;
    }

    public boolean waitForStatusMatching(String computationStatus, String studyNodeName, Computation compName, int timeoutInSeconds) {
        Node nodeIds = getNodeId(studyNodeName);
        RootNetwork rootNetwork = getCurrentRootNetwork();

        RetryPolicy<Boolean> retryPolicy = RetryPolicy.<Boolean>builder()
                .withDelay(Duration.ofMillis(1000))
                .withMaxRetries(timeoutInSeconds)
                .onRetriesExceeded(e -> LOGGER.warn("Waiting time exceeded"))
                .handleResult(Boolean.FALSE)
                .build();
        LOGGER.info("Wait for {} completion with status '{}' (max: {} sec)", compName.name(), computationStatus, retryPolicy.getConfig().getMaxRetries());
        return Failsafe.with(retryPolicy).get(() -> statusMatching(computationStatus, nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, compName));
    }

    // --------------------------------------------------------
    public int getIntParameter(String paramName, int defaultValue) {
        return configIntParameters.getOrDefault(paramName, defaultValue);
    }
}
