package org.gridsuite.bddtests.common;

import com.fasterxml.jackson.databind.JsonNode;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.gridsuite.bddtests.directory.DirectoryRequests;
import org.gridsuite.bddtests.explore.ExploreRequests;
import org.gridsuite.bddtests.study.StudyRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        SECURITY_ANALYSIS,
        SENSITIVITY_ANALYSIS,
    }

    // cf same Enum in study-server
    public enum ReportType {
        NETWORK_MODIFICATION("NetworkModification"),
        LOADFLOW("LoadFlow"),
        SECURITY_ANALYSIS("SecurityAnalysis"),
        SENSITIVITY_ANALYSIS("SensitivityAnalysis");

        public final String messageKey;

        ReportType(String messageKey) {
            this.messageKey = messageKey;
        }
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
        public RootNetwork(String rootNetworkUuid, String studyUuid) {
            this.rootNetworkUuid = rootNetworkUuid;
            this.name = studyUuid;
        }

        public String rootNetworkUuid;
        public String name;
    }

    // set of element alias/name and their uuid, we want to use/memorise during a whole scenario, between the steps
    private Map<String, String> currentDirectoryIds;
    private Map<String, String> currentStudyIds;
    private Map<String, String> currentCaseIds;
    private Map<String, Node> currentNodeIds;
    private Map<String, RootNetwork> currentRootNetworkIds;
    private Map<String, String> currentContingencyListIds;
    private Map<String, String> currentFilterIds;
    private Map<String, JsonNode> currentJsonData;
    private Map<String, Integer> configIntParameters;
    private Map<String, JsonNode> currentCaseExtensions;
    // a tmp dir uuid possibly associated to a scenario
    private String tmpRootDirId = null;

    // CONSTANTS:
    // TODO providers could be retrieved with get request
    public static final ArrayList<String> LOADFLOW_PROVIDERS = new ArrayList<>(List.of("OpenLoadFlow"));
    public static final ArrayList<String> SENSITIVITY_PROVIDERS = new ArrayList<>(List.of("OpenLoadFlow"));
    public static final int MAX_WAITING_TIME_IN_SEC = 180;
    public static final int MAX_COMPUTATION_WAITING_TIME_IN_SEC = 300;
    public static final int MAX_EXPORT_WAITING_TIME_IN_SEC = 150;
    public static final String CURRENT_ELEMENT = "current";

    // types allowed in step : get "type" equipment "id" , and their corresponding Powsybl type used by search
    public static final Map<String, String> EQPT_TYPES = Map.ofEntries(
            Map.entry("lines", "LINE"),
            Map.entry("2-windings-transformers", "TWO_WINDINGS_TRANSFORMER"),
            Map.entry("generators", "GENERATOR"),
            Map.entry("loads", "LOAD"),
            Map.entry("voltage-levels", "VOLTAGE_LEVEL"),
            Map.entry("substations", "SUBSTATION"),
            Map.entry("shunt-compensators", "SHUNT_COMPENSATOR"),
            Map.entry("vsc-converter-stations", "VSC_CONVERTER_STATION"),
            Map.entry("static-var-compensators", "STATIC_VAR_COMPENSATOR"),
            Map.entry("lcc-converter-stations", "LCC_CONVERTER_STATION"),
            Map.entry("dangling-lines", "DANGLING_LINE"),
            Map.entry("batteries", "BATTERY"),
            Map.entry("hvdc-lines", "HVDC_LINE"),
            Map.entry("3-windings-transformers", "THREE_WINDINGS_TRANSFORMER")
    );

    // modification types
    public static final Map<String, String> MODIF_TYPES = Map.ofEntries(
            Map.entry("lines", "LINE_MODIFICATION"),
            Map.entry("2-windings-transformers", "TWO_WINDINGS_TRANSFORMER_MODIFICATION"),
            Map.entry("generators", "GENERATOR_MODIFICATION"),
            Map.entry("loads", "LOAD"),
            Map.entry("voltage-levels", "VOLTAGE_LEVEL_MODIFICATION"),
            Map.entry("substations", "SUBSTATION_MODIFICATION"),
            Map.entry("shunt-compensators", "SHUNT_COMPENSATOR_MODIFICATION"),
            Map.entry("batteries", "BATTERY_MODIFICATION")
    );

    public static final Map<String, String> OPERATORS = Map.ofEntries(
            Map.entry("+", "ADDITION"),
            Map.entry("-", "SUBTRACTION"),
            Map.entry("*", "MULTIPLICATION"),
            Map.entry("/", "DIVISION"),
            Map.entry("%", "PERCENTAGE")
    );

    // extensions
    public static final Map<String, String> EXTENTION_KEYS = Map.ofEntries(
            Map.entry("CGMES", ""),
            Map.entry("XIIDM", "iidm.import.xml.included.extensions")
    );

    public static final ArrayList<String> JSON_DATA_TYPES = new ArrayList<>(Arrays.asList("array", "object", "value"));

    public static final Logger LOGGER = LoggerFactory.getLogger(TestContext.class);

    // --------------------------------------------------------
    public TestContext() {
    }

    public void init() {
        LOGGER.info("BaseStepDefinitions init");
        currentStudyIds = new HashMap<>();
        currentDirectoryIds = new HashMap<>();
        currentCaseIds = new HashMap<>();
        currentNodeIds = new HashMap<>();
        currentRootNetworkIds = new HashMap<>();
        currentJsonData = new HashMap<>();
        currentContingencyListIds = new HashMap<>();
        currentFilterIds = new HashMap<>();
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

    public String getCaseId(String name) {
        String id = currentCaseIds.getOrDefault(name, null);
        assertNotNull("no case " + name, id);
        return id;
    }

    public String getStudyId(String name) {
        String id = currentStudyIds.getOrDefault(name, null);
        assertNotNull("no study " + name, id);
        return id;
    }

    public JsonNode getJsonData(String name) {
        JsonNode id = currentJsonData.getOrDefault(name, null);
        assertNotNull("no json data " + name, id);
        return id;
    }

    public JsonNode getCaseExtentions(String name) {
        JsonNode id = currentCaseExtensions.getOrDefault(name, null);
        assertNotNull("no case extension data " + name, id);
        return id;
    }

    public Node getNodeId(String name) {
        Node id = currentNodeIds.getOrDefault(name, null);
        assertNotNull("no node " + name, id);
        return id;
    }

    public RootNetwork getCurrentRootNetwork() {
        return currentRootNetworkIds.get(CURRENT_ELEMENT);
    }

    public String getContingencyListId(String name) {
        String id = currentContingencyListIds.getOrDefault(name, null);
        assertNotNull("no Contingency List " + name, id);
        return id;
    }

    public String getFilterId(String name) {
        String id = currentFilterIds.getOrDefault(name, null);
        assertNotNull("no Filter " + name, id);
        return id;
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

    public void setCurrentRootNetworkUuid(String uuid, String studyId) {
        currentRootNetworkIds.put(CURRENT_ELEMENT, new RootNetwork(uuid, studyId));
    }

    public void setData(String aliasName, JsonNode data) {
        currentJsonData.put(aliasName, data);
    }

    public void setCaseExtentions(String aliasName, JsonNode data) {
        currentCaseExtensions.put(aliasName, data);
    }

    public void setCurrentContingencyList(String aliasName, String uuid) {
        currentContingencyListIds.put(CURRENT_ELEMENT, uuid);
        currentContingencyListIds.put(aliasName, uuid);
    }

    public void setCurrentFilter(String aliasName, String uuid) {
        currentFilterIds.put(CURRENT_ELEMENT, uuid);
        currentFilterIds.put(aliasName, uuid);
    }

    public String getEquipmentType(String equipmentType) {
        assertTrue("Equipment type must be in " + EQPT_TYPES.keySet(), EQPT_TYPES.containsKey(equipmentType));
        return EQPT_TYPES.get(equipmentType);
    }

    public String getModificationType(String equipmentType) {
        assertTrue("Equipment type must be in " + MODIF_TYPES.keySet(), MODIF_TYPES.containsKey(equipmentType));
        return MODIF_TYPES.get(equipmentType);
    }

    public String getOperator(String operator) {
        assertTrue("Operator must be in " + OPERATORS.keySet(), OPERATORS.containsKey(operator));
        return OPERATORS.get(operator);
    }

    public String getExtensionKey(String caseType) {
        assertTrue("ExtensionKey must be in " + EXTENTION_KEYS.keySet(), EXTENTION_KEYS.containsKey(caseType));
        return EXTENTION_KEYS.get(caseType);
    }

    // --------------------------------------------------------
    public void createTmpRootDirectoryAs(String aliasName, String owner, boolean noRemove) {
        String dirName = "bddtmp_" + UUID.randomUUID();
        LOGGER.info("Creating scenario temporary root dir '{}'", dirName);
        String dirId = createRootDirectory(dirName, aliasName, "", owner);
        if (!noRemove) {
            tmpRootDirId = dirId;
        }
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
        assertNotNull("Could not create root directory " + dirName, rootDirId);
        setCurrentDirectory(aliasName, rootDirId);
        return rootDirId;
    }

    // --------------------------------------------------------
    public void createDirectory(String dirName, String parentName, String owner) {
        String parentId = getDirId(parentName);
        String dirId = DirectoryRequests.getInstance().createDirectory(dirName, parentId, owner);
        assertNotNull("Could not create directory " + dirName + " in " + parentName, dirId);
        setCurrentDirectory(dirName, dirId);
    }

    // --------------------------------------------------------
    public String createDirectoryFromId(String aliasName, String dirName, String parentId, String owner) {
        String dirId = DirectoryRequests.getInstance().createDirectory(dirName, parentId, owner);
        assertNotNull("Could not create directory " + dirName + " in parentId " + parentId, dirId);
        setCurrentDirectory(aliasName, dirId);
        return dirId;
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

    public void executeAndWaitForStudyCreation(Runnable asyncRequest, String studyName, String directoryName, int secondsTimeout) {
        String dirId = getDirId(directoryName);
        NotificationWaiter.executeAndWaitForStudyCreation(asyncRequest, studyName, dirId, secondsTimeout);
    }

    // --------------------------------------------------------
    public String getElementFrom(String eltName, String eltType, String directoryName) {
        String dirId = getDirId(directoryName);
        String user = EnvProperties.getInstance().getUserName();
        String eltId = DirectoryRequests.getInstance().getElementId(user, dirId, eltType, eltName);
        assertNotNull("Cannot find " + eltType + " named " + eltName + " in directory " + directoryName, eltId);
        return eltId;
    }

    // --------------------------------------------------------
    public void searchEquipment(String equipmentType, String equipmentName, String studyNodeName, String alias, String fieldSelector) {
        // translate type before passing it to search REST API
        String eqptSearchType = "";
        if (!equipmentType.isEmpty()) {
            eqptSearchType = EQPT_TYPES.get(equipmentType);
        }
        Node nodeIds = getNodeId(studyNodeName);
        RootNetwork rootNetwork = getCurrentRootNetwork();

        boolean inUpstreamBuiltParentNode = false;  // Should search in upstream built node
        JsonNode eqtData = StudyRequests.getInstance().searchEquipment(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, equipmentName, fieldSelector, inUpstreamBuiltParentNode, eqptSearchType);
        assertNotNull("Equipment not found in network: " + equipmentName, eqtData);
        LOGGER.info("searchEquipment '{}' : '{}'", equipmentName, eqtData);
        assertTrue("Equipment data should be an array: " + equipmentName, eqtData.isArray());
        currentJsonData.put(alias, eqtData);
    }

    // --------------------------------------------------------
    private boolean statusMatching(String expectedStatus, String studyId, String rootNetworkUuid, String nodeId, Computation compName) {
        switch (compName) {
            case LOADFLOW:
                return expectedStatus.equalsIgnoreCase(StudyRequests.getInstance().getLoadFlowInfos(studyId, rootNetworkUuid, nodeId));
            case SECURITY_ANALYSIS:
                return expectedStatus.equalsIgnoreCase(StudyRequests.getInstance().getSecurityAnalysisStatus(studyId, rootNetworkUuid, nodeId));
            case SENSITIVITY_ANALYSIS :
                return expectedStatus.equalsIgnoreCase(StudyRequests.getInstance().getSensitivityStatus(studyId, rootNetworkUuid, nodeId));
            default:
                assertTrue("bad computation name", true);
        }
        return false;
    }

    public boolean waitForStatusMatching(String computationStatus, String studyNodeName, Computation compName, int timeoutInSeconds) {
        Node nodeIds = getNodeId(studyNodeName);
        RootNetwork rootNetwork = getCurrentRootNetwork();

        RetryPolicy<Boolean> retryPolicy = new RetryPolicy<Boolean>()
                .withDelay(Duration.ofMillis(1000))
                .withMaxRetries(timeoutInSeconds)
                .onRetriesExceeded(e -> LOGGER.warn("Waiting time exceeded"))
                .handleResult(Boolean.FALSE);
        LOGGER.info("Wait for {} completion with status '{}' (max: {} sec)", compName.name(), computationStatus, retryPolicy.getMaxRetries());
        return Failsafe.with(retryPolicy).get(() -> statusMatching(computationStatus, nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, compName));
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
        LOGGER.info("valuesEqualityList keys = '{}'", listAttr);

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
    public void deleteNode(String studyNodeName, int expectedHttpCode) {
        Node nodeIds = getNodeId(studyNodeName);
        int httpCode = StudyRequests.getInstance().deleteNode(nodeIds.studyId, nodeIds.nodeId);
        assertEquals("Node deletion unexpected http return code", expectedHttpCode, httpCode);
    }

    // --------------------------------------------------------
    public void moveElement(List<String> eltIds, String targetDirName, int expectedHttpCode) {
        int httpCode = DirectoryRequests.getInstance().moveElement(eltIds, getDirId(targetDirName), EnvProperties.getInstance().getUserName());
        assertEquals("Move element unexpected http return code", expectedHttpCode, httpCode);
    }

    // --------------------------------------------------------
    public String createNode(String newNodeName, String studyNodeName, String creationMode) {
        Node nodeIds = getNodeId(studyNodeName);
        return StudyRequests.getInstance().createNode(nodeIds.studyId, nodeIds.nodeId, newNodeName, creationMode);
    }

    // --------------------------------------------------------
    public String createSecuritySequence(String studyNodeName) {
        Node nodeIds = getNodeId(studyNodeName);
        return StudyRequests.getInstance().createSequence(nodeIds.studyId, nodeIds.nodeId, "SECURITY_SEQUENCE");
    }

    // --------------------------------------------------------
    public void directoryPresence(String dirName) {
        String dirId = getDirId(dirName);
        JsonNode data = DirectoryRequests.getInstance().getElementInfo(dirId);
        assertNotNull(data);
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

    // --------------------------------------------------------
    public void setIntParameter(String paramName, int paramIntValue) {
        configIntParameters.put(paramName, paramIntValue);
    }

    // --------------------------------------------------------
    public int getIntParameter(String paramName, int defaultValue) {
        return configIntParameters.getOrDefault(paramName, defaultValue);
    }
}
