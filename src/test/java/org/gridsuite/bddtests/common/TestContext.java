/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests.common;

import com.fasterxml.jackson.databind.JsonNode;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.gridsuite.bddtests.directory.DirectoryRequests;
import org.gridsuite.bddtests.explore.ExploreRequests;
import org.gridsuite.bddtests.study.StudyRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class TestContext {

    public enum Computation {
        LOADFLOW,
        SECURITY_ANALYSIS
    }

    public static class Node {
        public Node(String nId, String sId) {
            nodeId = nId;
            studyId = sId;
        }

        public String nodeId;
        public String studyId;
    }

    // set of element alias/name and their uuid, we want to use/memorise during a whole scenario, between the steps
    private Map<String, String> currentDirectoryIds;
    private Map<String, String> currentStudyIds;
    private Map<String, String> currentCaseIds;
    private Map<String, Node> currentNodeIds;
    private Map<String, String> currentContingencyListIds;
    private Map<String, String> currentFilterIds;
    private Map<String, JsonNode> currentJsonData;
    // a tmp dir uuid possibly associated to a scenario
    private String tmpRootDirId = null;

    // CONSTANTS:
    public static final ArrayList<String> LOADFLOW_PROVIDERS = new ArrayList<>(Arrays.asList("Hades2", "OpenLoadFlow"));
    public static final int MAX_WAITING_TIME_IN_SEC = 120;
    public static final int MAX_COMPUTATION_WAITING_TIME_IN_SEC = 300;
    public static final String CURRENT_ELEMENT = "current";

    // types allowed in step : get "type" equipment "id" , and their corresponding Powsybl type used by search
    public static final Map<String, String> EQPT_TYPES_1 = Map.ofEntries(
            new AbstractMap.SimpleEntry<>("lines", "LINE"),
            new AbstractMap.SimpleEntry<>("2-windings-transformers", "TWO_WINDINGS_TRANSFORMER"),
            new AbstractMap.SimpleEntry<>("generators", "GENERATOR"),
            new AbstractMap.SimpleEntry<>("loads", "LOAD"),
            new AbstractMap.SimpleEntry<>("voltage-levels", "VOLTAGE_LEVEL"),
            new AbstractMap.SimpleEntry<>("substations", "SUBSTATION"),
            new AbstractMap.SimpleEntry<>("shunt-compensators", "SHUNT_COMPENSATOR")
    );
    // types allowed in step : get "type" equipment "id" with substation "sub"
    public static final Map<String, String> EQPT_TYPES_2 = Map.ofEntries(
            new AbstractMap.SimpleEntry<>("vsc-converter-stations", "HVDC_CONVERTER_STATION"),  // strange mapping
            new AbstractMap.SimpleEntry<>("static-var-compensators", "STATIC_VAR_COMPENSATOR"),
            new AbstractMap.SimpleEntry<>("lcc-converter-stations", "LCC_CONVERTER_STATION"),
            new AbstractMap.SimpleEntry<>("dangling-lines", "DANGLING_LINE"),
            new AbstractMap.SimpleEntry<>("batteries", "BATTERY"),
            new AbstractMap.SimpleEntry<>("hvdc-lines", "HVDC_LINE"),
            new AbstractMap.SimpleEntry<>("3-windings-transformers", "THREE_WINDINGS_TRANSFORMER")
    );
    public static final ArrayList<String> JSON_DATA_TYPES = new ArrayList<>(Arrays.asList("array", "object", "value"));

    // all equipment type we can use in our "get equipment" steps
    private final Map<String, String> equipmentMap;

    public static final Logger LOGGER = LoggerFactory.getLogger(TestContext.class);

    // --------------------------------------------------------
    public TestContext() {
        equipmentMap = new HashMap<>(EQPT_TYPES_1);
        equipmentMap.putAll(EQPT_TYPES_2);
    }

    public void init() {
        LOGGER.info("BaseStepDefinitions init");
        currentStudyIds = new HashMap<>();
        currentDirectoryIds = new HashMap<>();
        currentCaseIds = new HashMap<>();
        currentNodeIds = new HashMap<>();
        currentJsonData = new HashMap<>();
        currentContingencyListIds = new HashMap<>();
        currentFilterIds = new HashMap<>();
        tmpRootDirId = null;
    }

    public void reset() {
        LOGGER.info("BaseStepDefinitions reset");
        // Remove the whole tmp dir, if used by the scenario
        if (tmpRootDirId != null) {
            LOGGER.info("Remove current tmp root dir");
            String user = EnvProperties.getInstance().getUserName();
            DirectoryRequests.getInstance().removeElement(tmpRootDirId, user);
        }
        currentStudyIds = null;
        currentDirectoryIds = null;
        currentCaseIds = null;
        currentNodeIds = null;
        currentJsonData = null;
        currentContingencyListIds = null;
        currentFilterIds = null;
    }

    // --------------------------------------------------------
    public String getDirId(String name) {
        String id = currentDirectoryIds.getOrDefault(name, null);
        assertNotNull("no directory " + name, id);
        return id;
    }

    public String getDirId() {
        return getDirId(CURRENT_ELEMENT);
    }

    public String getCaseId(String name) {
        String id = currentCaseIds.getOrDefault(name, null);
        assertNotNull("no case " + name, id);
        return id;
    }

    public String getCaseId() {
        return getCaseId(CURRENT_ELEMENT);
    }

    public String getStudyId(String name) {
        String id = currentStudyIds.getOrDefault(name, null);
        assertNotNull("no study " + name, id);
        return id;
    }

    public String getStudyId() {
        return getStudyId(CURRENT_ELEMENT);
    }

    public JsonNode getJsonData(String name) {
        JsonNode id = currentJsonData.getOrDefault(name, null);
        assertNotNull("no json data " + name, id);
        return id;
    }

    public JsonNode getJsonData() {
        return getJsonData(CURRENT_ELEMENT);
    }

    public Node getNodeId(String name) {
        Node id = currentNodeIds.getOrDefault(name, null);
        assertNotNull("no node " + name, id);
        return id;
    }

    public Node getNodeId() {
        return getNodeId(CURRENT_ELEMENT);
    }

    public String getContingencyListId(String name) {
        String id = currentContingencyListIds.getOrDefault(name, null);
        assertNotNull("no Contingency List " + name, id);
        return id;
    }

    public String getContingencyListId() {
        return getContingencyListId(CURRENT_ELEMENT);
    }

    public String getFilterId(String name) {
        String id = currentFilterIds.getOrDefault(name, null);
        assertNotNull("no Filter " + name, id);
        return id;
    }

    public String getFilterId() {
        return getFilterId(CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    public void setStudy(String aliasName, String uuid) {
        currentStudyIds.put(aliasName, uuid);
    }

    public void setCurrentStudy(String uuid) {
        setStudy(CURRENT_ELEMENT, uuid);
    }

    public void setDirectory(String aliasName, String uuid) {
        currentDirectoryIds.put(aliasName, uuid);
    }

    public void setCurrentDirectory(String uuid) {
        setDirectory(CURRENT_ELEMENT, uuid);
    }

    public void setCase(String aliasName, String uuid) {
        currentCaseIds.put(aliasName, uuid);
    }

    public void setCurrentCase(String uuid) {
        setCase(CURRENT_ELEMENT, uuid);
    }

    public void setNode(String aliasName, String uuid, String studyId) {
        currentNodeIds.put(aliasName, new Node(uuid, studyId));
    }

    public void setCurrentNode(String uuid, String studyId) {
        setNode(CURRENT_ELEMENT, uuid, studyId);
    }

    public void setData(String aliasName, JsonNode data) {
        currentJsonData.put(aliasName, data);
    }

    public void setContingencyList(String aliasName, String uuid) {
        currentContingencyListIds.put(aliasName, uuid);
    }

    public void setCurrentContingencyList(String uuid) {
        setContingencyList(CURRENT_ELEMENT, uuid);
    }

    public void setFilter(String aliasName, String uuid) {
        currentFilterIds.put(aliasName, uuid);
    }

    public void setCurrentFilter(String uuid) {
        setFilter(CURRENT_ELEMENT, uuid);
    }

    public void checkEqptType1(String equipmentType) {
        assertTrue("Equipment type must be in " + EQPT_TYPES_1.keySet(), EQPT_TYPES_1.containsKey(equipmentType));
    }

    public void checkEqptType2(String equipmentType) {
        assertTrue("Equipment type (with substation) must be in " + EQPT_TYPES_2.keySet(), EQPT_TYPES_2.containsKey(equipmentType));
    }

    public String checkEqptType(String equipmentType) {
        assertTrue("Equipment type must be in " + equipmentMap.keySet(), equipmentMap.containsKey(equipmentType));
        return equipmentMap.get(equipmentType);
    }

    // --------------------------------------------------------
    public void createTmpRootDirectoryAs(String aliasName, String owner, boolean isPrivate) {
        String dirName = "bddtmp_" + UUID.randomUUID();
        LOGGER.info("Creating scenario temporary root dir '{}'", dirName);
        tmpRootDirId = createRootDirectory(dirName, aliasName, isPrivate, "", owner);
    }

    // --------------------------------------------------------
    public String createRootDirectory(String dirName, String aliasName, boolean isPrivate, String desc, String owner) {
        String rootDirId = DirectoryRequests.getInstance().createRootDirectory(dirName, owner, isPrivate, desc);
        assertNotNull("Could not create root directory " + dirName, rootDirId);
        currentDirectoryIds.put(aliasName, rootDirId);
        return rootDirId;
    }

    // --------------------------------------------------------
    public void createDirectory(String dirName, String parentName, boolean isPrivate, String owner) {
        String parentId = getDirId(parentName);
        String dirId = DirectoryRequests.getInstance().createDirectory(dirName, parentId, isPrivate, owner);
        assertNotNull("Could not create directory " + dirName + " in " + parentName, dirId);
        currentDirectoryIds.put(dirName, dirId);
    }

    // --------------------------------------------------------
    public String elementExists(String elementName, String elementType, String directoryName, boolean shouldExist) {
        String dirId = getDirId(directoryName);
        String user = EnvProperties.getInstance().getUserName();
        String eltId = DirectoryRequests.getInstance().getElementId(user, dirId, elementType, elementName);
        if (shouldExist) {
            assertNotNull("Could not find " + elementType + " named " + elementName + " in " + directoryName, eltId);
        } else {
            assertNull("Should not find " + elementType + " named " + elementName + " in " + directoryName, eltId);
        }
        return eltId;
    }

    // --------------------------------------------------------
    public String waitForElementCreation(String dirId, String elementType, String elementName) {
        // check element creation in target directory, and return its uuid
        RetryPolicy<String> retryPolicyDirectory = new RetryPolicy<String>()
                .withDelay(Duration.ofMillis(1000))
                .withMaxRetries(MAX_WAITING_TIME_IN_SEC)
                .onRetriesExceeded(e -> LOGGER.warn("Waiting time exceeded"))
                .handleResult(null);
        LOGGER.info("Wait for '{}' {} element creation in directory (max: {} sec)", elementName, elementType, retryPolicyDirectory.getMaxRetries());
        String user = EnvProperties.getInstance().getUserName();
        return Failsafe.with(retryPolicyDirectory).get(() -> DirectoryRequests.getInstance().getElementId(user, dirId, elementType, elementName));
    }

    public void waitForStudyCreation(String studyName, String directoryName, int secondsTimeout) {
        String dirId = getDirId(directoryName);

        // check element creation in target directory
        String studyId = waitForElementCreation(dirId, "STUDY", studyName);
        assertNotNull("Study not created in directory with name " + studyName, studyId);
        currentStudyIds.put(studyName, studyId);

        // check study creation completion
        final String sId = studyId;
        RetryPolicy<Boolean> retryPolicyStudy = new RetryPolicy<Boolean>()
                .withDelay(Duration.ofMillis(1000))
                .withMaxRetries(secondsTimeout)
                .onRetriesExceeded(e -> LOGGER.warn("Waiting time exceeded"))
                .handleResult(Boolean.FALSE);
        LOGGER.info("Wait for '{}' study creation completion (max: {} sec)", studyName, retryPolicyStudy.getMaxRetries());
        boolean studyExists = Failsafe.with(retryPolicyStudy).get(() -> StudyRequests.getInstance().existsStudy(sId));
        assertTrue("Study full creation not confirmed", studyExists);

        // make sure we can read the tree (existsStudy() can say "OK" because the study entity has been saved,
        // but the 2 default nodes may still be in creation)
        boolean ok = StudyRequests.getInstance().checkNodeTree(sId);
        RetryPolicy<Boolean> retryPolicyTree = new RetryPolicy<Boolean>()
                .withDelay(Duration.ofMillis(500))
                .withMaxRetries(MAX_WAITING_TIME_IN_SEC)
                .onRetriesExceeded(e -> LOGGER.warn("Waiting time exceeded"))
                .handleResult(Boolean.FALSE);
        LOGGER.info("Wait for '{}' default tree creation completion (max: {} sec)", studyName, retryPolicyStudy.getMaxRetries());
        boolean treeExists = Failsafe.with(retryPolicyStudy).get(() -> StudyRequests.getInstance().checkNodeTree(sId));
        assertTrue("Study tree full creation not confirmed", treeExists);

    }

    // --------------------------------------------------------
    public String getElementFrom(String eltName, String eltType, String directoryName) {
        String dirId = getDirId(directoryName);
        String user = EnvProperties.getInstance().getUserName();
        String eltId = DirectoryRequests.getInstance().getElementId(user, dirId, eltType, eltName);
        assertNotNull("Cannot find " + eltType + " named " + eltName, eltId);
        return eltId;
    }

    // --------------------------------------------------------
    public void searchEquipment(String equipmentType, String equipmentName, String studyNodeName, String alias, String fieldSelector) {
        // translate type before passing it to search REST API
        String eqptSearchType = "";
        if (!equipmentType.isEmpty()) {
            eqptSearchType = equipmentMap.get(equipmentType);
        }
        Node nodeIds = getNodeId(studyNodeName);

        boolean inUpstreamBuiltParentNode = false;  // Should search in upstream built node
        JsonNode eqtData = StudyRequests.getInstance().searchEquipment(nodeIds.studyId, nodeIds.nodeId, equipmentName, fieldSelector, inUpstreamBuiltParentNode, eqptSearchType);
        assertNotNull("Equipment not found in network: " + equipmentName, eqtData);
        LOGGER.info("searchEquipment '{}' : '{}'", equipmentName, eqtData);
        assertTrue("Equipment data should be an array: " + equipmentName, eqtData.isArray());
        currentJsonData.put(alias, eqtData);
    }

    // --------------------------------------------------------
    private boolean statusMatching(String expectedStatus, String studyId, String nodeId, Computation compName) {
        switch (compName) {
            case LOADFLOW:
                return expectedStatus.equalsIgnoreCase(StudyRequests.getInstance().getLoadFlowInfos(studyId, nodeId));
            case SECURITY_ANALYSIS:
                return expectedStatus.equalsIgnoreCase(StudyRequests.getInstance().getSecurityAnalysisStatus(studyId, nodeId));
            default:
                assertTrue("bad computation name", true);
        }
        return false;
    }

    public boolean waitForStatusMatching(String computationStatus, String studyNodeName, Computation compName, int timeoutInSeconds) {
        Node nodeIds = getNodeId(studyNodeName);

        RetryPolicy<Boolean> retryPolicy = new RetryPolicy<Boolean>()
                .withDelay(Duration.ofMillis(1000))
                .withMaxRetries(timeoutInSeconds)
                .onRetriesExceeded(e -> LOGGER.warn("Waiting time exceeded"))
                .handleResult(Boolean.FALSE);
        LOGGER.info("Wait for {} completion with status '{}' (max: {} sec)", compName.name(), computationStatus, retryPolicy.getMaxRetries());
        return Failsafe.with(retryPolicy).get(() -> statusMatching(computationStatus, nodeIds.studyId, nodeIds.nodeId, compName));
    }

    // --------------------------------------------------------
    public void valuesEqualityList(String values1Id, String values2Id, String format, String attrList, boolean equal) {
        JsonNode jsonValues1 = getJsonData(values1Id);
        JsonNode jsonValues2 = getJsonData(values2Id);
        assertNotNull("No current json data for " + values2Id, jsonValues2);

        List<String> values1 = new ArrayList<>();
        List<String> values2 = new ArrayList<>();

        DecimalFormat df = null;
        if (!format.equalsIgnoreCase("exact")) {
            // use fixed locale to always have the same decimal separator (US => .)
            df = new DecimalFormat(format, new DecimalFormatSymbols(Locale.US));
        }

        List<String> listAttr = Stream.of(attrList.split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        if (listAttr.size() == 1 && listAttr.get(0).equalsIgnoreCase("all")) {
            // infer all attributes from Json data
            listAttr.clear();
            Set<String> keys = new HashSet<>();
            Iterator<String> iterator = jsonValues1.fieldNames();
            iterator.forEachRemaining(keys::add);
            iterator = jsonValues2.fieldNames();
            iterator.forEachRemaining(keys::add);
            listAttr = List.copyOf(keys);
        }
        LOGGER.info("valuesEqualityList keys = '{}'", listAttr.toString());

        for (String k : listAttr) {
            assertTrue("Attribute " + k + " not found in alias " + values1Id, jsonValues1.has(k));
            assertTrue("Attribute " + k + " not found in alias " + values2Id, jsonValues2.has(k));
            if (df != null) {
                // round half-even decimal value
                values1.add(df.format(jsonValues1.get(k).asDouble()));
                values2.add(df.format(jsonValues2.get(k).asDouble()));
            } else {
                // exact text value
                values1.add(jsonValues1.get(k).asText());
                values2.add(jsonValues2.get(k).asText());
            }
        }

        LOGGER.info("valuesEqualityList '{}' '{}' '{}' ", equal, values1, values2);
        if (equal) {
            assertEquals("Attribute list should be equal", values1, values2);
        } else {
            assertNotEquals("Attribute list should be different", values1, values2);
        }
    }

    // --------------------------------------------------------
    public void upsertEquipment(String equipmentType, String studyNodeName, String resourceFileName, boolean creation) {
        assertTrue("Equipment type must be in " + equipmentMap.keySet(), equipmentMap.containsKey(equipmentType));
        Node nodeIds = getNodeId(studyNodeName);

        // just look into resources dir !
        Path resourceFile = Paths.get("src", "test", "resources", resourceFileName);
        assertTrue("Cannot find resource file named " + resourceFile.toFile().getAbsolutePath(),
                Files.exists(resourceFile) && Files.isRegularFile(resourceFile));
        String fileContent = Utils.readFileContent(resourceFile, -1);
        assertTrue("Cannot read content from resource file named " + resourceFile.toFile().getAbsolutePath(), fileContent != null && fileContent.length() > 0);

        StudyRequests.getInstance().upsertEquipment(nodeIds.studyId, nodeIds.nodeId, equipmentType, fileContent, creation);
    }

    // --------------------------------------------------------
    public void createFilterIn(String elementName, String directoryName, String filterType) {
        String dirId = getDirId(directoryName);
        String user = EnvProperties.getInstance().getUserName();
        ExploreRequests.getInstance().createDefaultFilter(elementName, "empty " + filterType + " filter", dirId, user, filterType);
        // retrieve the element
        String fId = waitForElementCreation(dirId, "FILTER", elementName);
        assertNotNull("Filter not created in directory with name " + elementName, fId);
        setFilter(elementName, fId);
    }

    // --------------------------------------------------------
    public void deleteNode(String studyNodeName, int expectedHttpCode) {
        Node nodeIds = getNodeId(studyNodeName);
        int httpCode = StudyRequests.getInstance().deleteNode(nodeIds.studyId, nodeIds.nodeId);
        assertEquals("Node deletion unexpected http return code", expectedHttpCode, httpCode);
    }

    // --------------------------------------------------------
    public void moveElement(String eltId, String targetDirName, int expectedHttpCode) {
        int httpCode = DirectoryRequests.getInstance().moveElement(eltId, getDirId(targetDirName), EnvProperties.getInstance().getUserName());
        assertEquals("Move element unexpected http return code", expectedHttpCode, httpCode);
    }

    // --------------------------------------------------------
    public void createNode(String newNodeName, String studyNodeName, String creationMode) {
        Node nodeIds = getNodeId(studyNodeName);

        StudyRequests.getInstance().createNode(nodeIds.studyId, nodeIds.nodeId, newNodeName, creationMode);
    }

    // --------------------------------------------------------
    public void directoryAccess(String dirName, boolean isPrivate) {
        String dirId = getDirId(dirName);
        JsonNode data = DirectoryRequests.getInstance().getElementInfo(dirId);
        assertNotNull(data);
        final String isPrivateKey = "/accessRights/isPrivate";
        assertTrue("directory content must have " + isPrivateKey, data.at(isPrivateKey).isValueNode());
        assertEquals(String.valueOf(isPrivate), data.at(isPrivateKey).asText());
    }

    // --------------------------------------------------------
    public void elementOwner(String dirName, String user) {
        String dirId = getDirId(dirName);
        JsonNode data = DirectoryRequests.getInstance().getElementInfo(dirId);
        assertNotNull(data);
        final String owner = "owner";
        assertTrue("element data must have " + owner, data.has(owner));
        assertTrue("element not owned by " + user, user.equalsIgnoreCase(data.get(owner).asText()));
    }
}
