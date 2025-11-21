package org.gridsuite.bddtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.gridsuite.bddtests.cases.CaseRequests;
import org.gridsuite.bddtests.common.EnvProperties;
import org.gridsuite.bddtests.common.NotificationWaiter;
import org.gridsuite.bddtests.common.TestContext;
import org.gridsuite.bddtests.common.Utils;
import org.gridsuite.bddtests.config.ConfigRequests;
import org.gridsuite.bddtests.explore.ExploreRequests;
import org.gridsuite.bddtests.networkconversion.NetworkConversionRequests;
import org.gridsuite.bddtests.study.StudyRequests;
import org.junit.platform.commons.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.gridsuite.bddtests.common.TestContext.MAX_COMPUTATION_WAITING_TIME_IN_SEC;
import static org.gridsuite.bddtests.common.TestContext.MAX_EXPORT_WAITING_TIME_IN_SEC;
import static org.junit.Assert.*;

public class StudySrvStepDefinitions {

    private final TestContext ctx;

    // DI with PicoContainer to share the same context among all steps classes
    public StudySrvStepDefinitions(TestContext ctx) {
        this.ctx = ctx;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(StudySrvStepDefinitions.class);

    // --------------------------------------------------------
    // setup before each scenario (before BACKGROUND) - should be only here => UNIQUE for all steps for all feature files
    @Before
    public void setup() {
        ctx.init();
    }

    // --------------------------------------------------------
    // teardown after each scenario - should be only here => UNIQUE for all steps for all feature files
    @After
    public void teardown() {
        ctx.reset();
    }

    // --------------------------------------------------------
    // BACKGROUND conditions

    // target platform
    @Given("using platform {string}")
    public void usingPlatform(String environmentName) {
        String envName;
        String platformNameProp = System.getProperty("using_platform");
        if (platformNameProp == null) {
            LOGGER.info("Using Gherkin platform = '{}'", environmentName);
            envName = environmentName;
        } else {
            LOGGER.info("Using Property using_platform = '{}'", platformNameProp);
            envName = platformNameProp;
        }
        assertTrue("Cannot load properties for env " + envName, EnvProperties.getInstance().init(envName));
    }

    // tmp root directory
    @Given("using tmp root dir {string}")
    public void usingTmpRootDir(String dirName) {
        EnvProperties.getInstance().setTmpRootDir(dirName);
    }

    // --------------------------------------------------------
    @Given("case {string} does not exist in {string}")
    public void caseDoesNotExistIn(String caseName, String directoryName) {
        ctx.elementExists(caseName, "CASE", directoryName, false);
    }

    // --------------------------------------------------------
    @Given("case {string} exists in {string}")
    public void caseExistIn(String caseName, String directoryName) {
        String caseId = ctx.elementExists(caseName, "CASE", directoryName, true);
        ctx.setCurrentCase(caseName, caseId);
        ctx.setCaseExtentions(caseName, getCaseExtensions(caseId));
    }

    // --------------------------------------------------------
    @When("create case {string} in {string} from resource {string}")
    public void createCaseInFromResource(String caseName, String directoryName, String caseFileName) {
        String dirId = ctx.getDirId(directoryName);
        Path resourceFile = Paths.get("src", "test", "resources", caseFileName);
        assertTrue("Cannot find resource file named " + resourceFile.toFile().getAbsolutePath(),
                Files.exists(resourceFile) && Files.isRegularFile(resourceFile));
        String user = EnvProperties.getInstance().getUserName();
        final String description = "STEP create_case_in_directory_from_resource";
        // async request
        ExploreRequests.getInstance().createCaseFromFile(caseName, resourceFile, description, dirId, user);

        // then wait for completion:
        // 1. check element creation in target directory
        String caseId = ctx.waitForElementCreation(dirId, "CASE", caseName);
        assertNotNull("Case not created in directory with name " + caseName, caseId);
        // 2. check case creation completion
        final String cId = caseId;
        RetryPolicy<Boolean> retryPolicyStudy = new RetryPolicy<Boolean>()
                .withDelay(Duration.ofMillis(1000))
                .withMaxRetries(TestContext.MAX_WAITING_TIME_IN_SEC)
                .onRetriesExceeded(e -> LOGGER.warn("Waiting time exceeded"))
                .handleResult(Boolean.FALSE);
        LOGGER.info("Wait for '{}' case creation completion (max: {} sec)", caseName, retryPolicyStudy.getMaxRetries());
        boolean studyExists = Failsafe.with(retryPolicyStudy).get(() -> CaseRequests.getInstance().existsCase(cId));
        assertTrue("Case full creation not confirmed", studyExists);

        ctx.setCurrentCase(caseName, caseId);
        ctx.setCaseExtentions(caseName, getCaseExtensions(caseId));
    }

    // --------------------------------------------------------
    private JsonNode getCaseExtensions(String caseId) {
        JsonNode paramsJson = NetworkConversionRequests.getInstance().getImportParameters(caseId);
        assertNotNull(paramsJson);
        assertTrue(paramsJson.has("formatName"));
        assertTrue(paramsJson.has("parameters"));

        String format = paramsJson.get("formatName").asText();
        String extensionsKey = ctx.getExtensionKey(format);
        List<String> extensions = new ArrayList<>();
        if (!StringUtils.isBlank(extensionsKey)) {
            Optional<JsonNode> extensionList = StreamSupport.stream(paramsJson.get("parameters").spliterator(), false)
                    .filter(n -> n.has("name") && n.has("possibleValues") && n.get("name").asText().equals(extensionsKey))
                    .findFirst();
            assertFalse("Cannot find parameters child named " + extensionsKey, extensionList.isEmpty());
            for (JsonNode v : extensionList.get().get("possibleValues")) {
                extensions.add(v.asText());
            }
        }

        // result json example: { "formatName": "XIIDM", "extensions": "a,b,c,d,e,f",}
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode resultJson = mapper.createObjectNode();
        resultJson.put("formatName", format);
        resultJson.put("extensions", String.join(",", extensions));
        return resultJson;
    }

    // --------------------------------------------------------
    @When("create study {string} in {string} from resource {string}")
    public void createStudyInDirectoryFromResource(String studyName, String directoryName, String caseFileName) {
        // just look into resources dir
        Path resourceFile = Paths.get("src", "test", "resources", caseFileName);
        assertTrue("Cannot find resource file named " + resourceFile.toFile().getAbsolutePath(),
                Files.exists(resourceFile) && Files.isRegularFile(resourceFile));
        createStudyFromFile(studyName, directoryName, resourceFile);
    }

    // --------------------------------------------------------
    @When("create study {string} in {string} from {string} file {string}")
    public void createStudyInDirectoryFromFile(String studyName, String directoryName, String subDir, String fileName) {
        // look into local filesystem in $BDD_DATA_DIR/<subDir> directory (not used so far)
        Path bddFile = Utils.getBddFile(subDir, fileName);
        createStudyFromFile(studyName, directoryName, bddFile);
    }

    private void createStudyFromFile(String studyName, String directoryName, Path filePath) {
        ctx.getDirId(directoryName);
        String user = EnvProperties.getInstance().getUserName();
        // 1: import case
        String caseId = CaseRequests.getInstance().createCaseFromFile(filePath, user);
        JsonNode caseExtensions = getCaseExtensions(caseId);
        // 2: create study
        String caseFormat = caseExtensions.get("formatName").asText();
        String extensions = caseExtensions.get("extensions").asText();
        createStudy(studyName, directoryName, caseId, caseFormat, extensions, false);
    }

    // --------------------------------------------------------
    @When("create study {string} in {string} from case {string}")
    public void createStudyInDirectoryFromCase(String studyName, String directoryName, String caseName) {
        JsonNode caseExtensions = ctx.getCaseExtentions(caseName);
        // use all existing extensions
        createStudyInDirectoryFromCaseWithExtensions(studyName, directoryName, caseName, caseExtensions.get("extensions").asText());
    }

    // --------------------------------------------------------
    @When("create study {string} in {string} from case {string} with extensions {string}")
    public void createStudyInDirectoryFromCaseWithExtensions(String studyName, String directoryName, String caseName, String extensions) {
        String caseId = ctx.getCaseId(caseName);
        JsonNode caseExtensions = ctx.getCaseExtentions(caseName);
        String caseFormat = caseExtensions.get("formatName").asText();
        createStudy(studyName, directoryName, caseId, caseFormat, extensions, true);
    }

    private void createStudy(String studyName, String directoryName, String caseId, String caseFormat, String extensions, boolean duplicateCase) {
        final String description = "STEP create_study_in_directory_from_case";
        String dirId = ctx.getDirId(directoryName);
        String user = EnvProperties.getInstance().getUserName();

        String extensionsKey = ctx.getExtensionKey(caseFormat);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        if (!StringUtils.isBlank(extensionsKey)) {
            body.put(extensionsKey, extensions);
        }
        ctx.executeAndWaitForStudyCreation(
                () -> ExploreRequests.getInstance().createStudyFromCase(studyName, caseId, description, dirId, user, caseFormat, body.toString(), duplicateCase),
                studyName,
                directoryName,
                TestContext.MAX_WAITING_TIME_IN_SEC);
    }

    // --------------------------------------------------------
    @When("duplicate study {string} expected name {string}")
    public void duplicateStudy(String studyName, String newStudyName) {
        duplicateStudyIn(studyName, null, newStudyName);
    }

    // --------------------------------------------------------
    @When("duplicate study {string} in {string} expected name {string}")
    public void duplicateStudyIn(String studyName, String targetDirectoryName, String expectedStudyName) {
        String studyId = ctx.getStudyId(studyName);
        String parentDirId = targetDirectoryName != null ? ctx.getDirId(targetDirectoryName) : null;
        String parentDirName = targetDirectoryName != null ? targetDirectoryName : TestContext.CURRENT_ELEMENT;
        ctx.executeAndWaitForStudyCreation(
                () -> ExploreRequests.getInstance().duplicateStudy(studyId, parentDirId),
                expectedStudyName,
                parentDirName,
                TestContext.MAX_WAITING_TIME_IN_SEC);
    }

    // --------------------------------------------------------
    @Given("study {string} does not exist in {string}")
    public void studyDoesNotExistIn(String studyName, String directoryName) {
        ctx.elementExists(studyName, "STUDY", directoryName, false);
    }

    // --------------------------------------------------------
    @Given("study {string} exist in {string}")
    public void studyExistIn(String studyName, String directoryName) {
        ctx.elementExists(studyName, "STUDY", directoryName, true);
    }

    // --------------------------------------------------------
    @When("get study {string} from {string}")
    public void getStudyFrom(String studyName, String directoryName) {
        getStudyFromAs(studyName, directoryName, null);
    }

    @When("open study {string} from {string}")
    public void openStudyFrom(String studyName, String directoryName) {
        getStudyFromAs(studyName, directoryName, null);
    }

    // --------------------------------------------------------
    // getStudy with an alias (so we can open/access N studies)
    @When("get study {string} from {string} as {string}")
    public void getStudyFromAs(String studyName, String directoryName, String alias) {
        String id = ctx.getElementFrom(studyName, "STUDY", directoryName);
        ctx.setCurrentStudy(alias != null ? alias : studyName, id);
    }

    // --------------------------------------------------------
    @When("get node {string}")
    public String getNode(String studyNodeName) {
        // by default we use the current/last study Id
        return getNodeFromAs(studyNodeName, TestContext.CURRENT_ELEMENT, null);
    }

    // --------------------------------------------------------
    @When("get node {string} as {string}")
    public String getNodeAs(String studyNodeName, String alias) {
        // by default we use the current/last study Id
        return getNodeFromAs(studyNodeName, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @When("get node {string} from {string}")
    public String getNodeFrom(String studyNodeName, String studyName) {
        return getNodeFromAs(studyNodeName, studyName, null);
    }

    // --------------------------------------------------------
    @When("get node {string} from {string} as {string}")
    public String getNodeFromAs(String studyNodeName, String studyName, String alias) {
        String studyId = ctx.getStudyId(studyName);
        // check existence + keep Id only
        String nodeId = StudyRequests.getInstance().getNodeId(studyId, studyNodeName);
        assertNotNull("No current tree node named " + studyNodeName, nodeId);
        ctx.setCurrentNode(alias != null ? alias : studyNodeName, nodeId, studyId);
        return nodeId;
    }

    // --------------------------------------------------------
    @Then("build status ok")
    public void buildStatusOk() {
        TestContext.Node nodeIds = ctx.getNodeId(TestContext.CURRENT_ELEMENT);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        checkBuildStatus(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, "BUILT");
    }

    // --------------------------------------------------------
    @Then("node not built")
    public void nodeNotBuilt() {
        TestContext.Node nodeIds = ctx.getNodeId(TestContext.CURRENT_ELEMENT);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        checkBuildStatus(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, "NOT_BUILT");
    }

    // --------------------------------------------------------
    @Then("build status {string} ok")
    public void buildStatusOkFrom(String studyNodeName) {
        String studyId = ctx.getStudyId(TestContext.CURRENT_ELEMENT);
        String nodeId = StudyRequests.getInstance().getNodeId(studyId, studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        assertNotNull("Unknown node " + studyNodeName, nodeId);
        checkBuildStatus(studyId, rootNetwork.rootNetworkUuid, nodeId, "BUILT");
    }

    // --------------------------------------------------------
    @Then("build status {string} error")
    public void buildStatusErrorFrom(String studyNodeName) {
        String studyId = ctx.getStudyId(TestContext.CURRENT_ELEMENT);
        String nodeId = StudyRequests.getInstance().getNodeId(studyId, studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        assertNotNull("Unknown node " + studyNodeName, nodeId);
        checkBuildStatus(studyId, rootNetwork.rootNetworkUuid, nodeId, "BUILT_WITH_ERROR");
    }

    // --------------------------------------------------------
    @Then("build status error")
    public void buildStatusError() {
        TestContext.Node nodeIds = ctx.getNodeId(TestContext.CURRENT_ELEMENT);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        checkBuildStatus(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, "BUILT_WITH_ERROR");
    }

    // --------------------------------------------------------
    @Then("build status {string} warning")
    public void buildStatusWarningFrom(String studyNodeName) {
        String studyId = ctx.getStudyId(TestContext.CURRENT_ELEMENT);
        String nodeId = StudyRequests.getInstance().getNodeId(studyId, studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        assertNotNull("Unknown node " + studyNodeName, nodeId);
        checkBuildStatus(studyId, rootNetwork.rootNetworkUuid, nodeId, "BUILT_WITH_WARNING");
    }

    // --------------------------------------------------------
    @Then("build status warning")
    public void buildStatusWarning() {
        TestContext.Node nodeIds = ctx.getNodeId(TestContext.CURRENT_ELEMENT);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        checkBuildStatus(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, "BUILT_WITH_WARNING");
    }

    // --------------------------------------------------------
    private void checkBuildStatus(String studyId, String rootNetworkUuid, String studyNodeId, String statusValue) {
        String status = StudyRequests.getInstance().builtStatus(studyId, rootNetworkUuid, studyNodeId);
        assertNotNull("Could not get build status", status);
        assertEquals("Bad status", statusValue, status);
    }

    // --------------------------------------------------------
    @When("build node")
    public void buildNode() {
        TestContext.Node nodeIds = ctx.getNodeId(TestContext.CURRENT_ELEMENT);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        buildNodeFromId(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId);
    }

    // --------------------------------------------------------
    @When("build node {string}")
    public void buildNodeFromName(String studyNodeName) {
        buildNodeFromNameStudy(studyNodeName, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("build node {string} from {string}")
    public void buildNodeFromNameStudy(String studyNodeName, String studyName) {
        String studyId = ctx.getStudyId(studyName);
        String nodeId = StudyRequests.getInstance().getNodeId(studyId, studyNodeName);
        String rootNetworkUuid = StudyRequests.getInstance().getFirstRootNetworkId(studyId);
        if (nodeId == null) {
            // accept a build on a node than has not been selected/get before
            nodeId = getNode(studyNodeName);
        }
        buildNodeFromId(studyId, rootNetworkUuid, nodeId);
    }

    // --------------------------------------------------------
    private boolean isNodeBuilt(String studyId, String rootNetworkUuid, String nodeId) {
        String status = StudyRequests.getInstance().builtStatus(studyId, rootNetworkUuid, nodeId);
        assertNotNull("Could not get build status", status);
        return status.matches("BUILT|BUILT_WITH_ERROR|BUILT_WITH_WARNING");
    }

    // --------------------------------------------------------
    private void buildNodeFromId(String studyId, String rootNetworkUuid, String nodeId) {
        if (!isNodeBuilt(studyId, rootNetworkUuid, nodeId)) {
            // async request
            StudyRequests.getInstance().buildNode(studyId, rootNetworkUuid, nodeId);

            // then wait for completion
            final String sId = studyId;
            final String nId = nodeId;
            RetryPolicy<Boolean> retryPolicyStudy = new RetryPolicy<Boolean>()
                    .withDelay(Duration.ofMillis(1000))
                    .withMaxRetries(TestContext.MAX_WAITING_TIME_IN_SEC)
                    .onRetriesExceeded(e -> LOGGER.warn("Waiting time exceeded"))
                    .handleResult(Boolean.FALSE);
            boolean nodeBuilt = Failsafe.with(retryPolicyStudy).get(() -> isNodeBuilt(sId, rootNetworkUuid, nId));
            assertTrue("Node build not confirmed", nodeBuilt);
        }
    }

    // --------------------------------------------------------
    @When("unbuild node")
    public void unbuildNode() {
        TestContext.Node nodeIds = ctx.getNodeId(TestContext.CURRENT_ELEMENT);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        // sync request
        StudyRequests.getInstance().unbuildNode(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId);
    }

    // --------------------------------------------------------
    @When("get first root network from {string}")
    public void getFirstRootNetworkFrom(String studyNodeName) {
        String studyId = ctx.getStudyId(studyNodeName);
        String rootNetworkId = StudyRequests.getInstance().getFirstRootNetworkId(studyId);
        ctx.setCurrentRootNetworkUuid(rootNetworkId, studyId);
    }

    // --------------------------------------------------------
    @When("get node data {string} as {string}")
    public void getNodeData(String studyNodeName, String alias) {
        getNodeDataFrom(studyNodeName, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @Then("check {string} children nodes are not built")
    public void checkChildrenNodesAreNotBuilt(String studyNodeName) {
        JsonNode nodeData = StudyRequests.getInstance().getNodeData(ctx.getStudyId(TestContext.CURRENT_ELEMENT), Optional.of(ctx.getCurrentRootNetwork().rootNetworkUuid), studyNodeName, "name");
        assertNotNull("No current tree node named " + studyNodeName, nodeData);
        checkChildrenNodesAreNotBuilt(nodeData);
    }

    private void checkChildrenNodesAreNotBuilt(JsonNode nodeData) {
        if (nodeData.has("children")) {
            ArrayNode children = (ArrayNode) nodeData.get("children");
            for (JsonNode child : children) {
                assertTrue("Child node " + child.get("name").asText() + " is built", child.get("nodeBuildStatus").get("globalBuildStatus").asText().equalsIgnoreCase("NOT_BUILT"));
                checkChildrenNodesAreNotBuilt(child);
            }
        }
    }

    // --------------------------------------------------------
    @When("get node data {string} from {string} as {string}")
    public void getNodeDataFrom(String studyNodeName, String studyName, String alias) {
        String studyId = ctx.getStudyId(studyName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        // check existence + keep all json data
        JsonNode nodeData = StudyRequests.getInstance().getNodeData(studyId, Optional.of(rootNetwork.rootNetworkUuid), studyNodeName, "name");
        assertNotNull("No current tree node named " + studyNodeName, nodeData);
        ctx.setData(alias, nodeData);
    }

    // --------------------------------------------------------
    @When("get {string} equipment {string} as {string}")
    public void getEquipment(String equipmentType, String equipmentId, String alias) {
        getEquipmentFrom(equipmentType, equipmentId, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @When("get {string} equipment {string} from {string} as {string}")
    public void getEquipmentFrom(String equipmentType, String equipmentId, String studyNodeName, String alias) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        JsonNode data = StudyRequests.getInstance().getEquipmentData(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, ctx.getEquipmentType(equipmentType), equipmentId, "TAB");
        assertNotNull("no data found for equipment " + equipmentType + " with ID " + equipmentId, data);
        ctx.setData(alias, data);
    }

    private void getAllEquipmentFrom(String equipmentType, String studyNodeName) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        StudyRequests.getInstance().getAllEquipmentsData(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, ctx.getEquipmentType(equipmentType), "TAB");
    }

    // --------------------------------------------------------
    @When("search {string} equipment with name {string} as {string}")
    public void searchEquipmentWithName(String equipmentType, String equipmentName, String alias) {
        searchEquipmentWithNameFrom(equipmentType, equipmentName, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @When("search {string} equipment with name {string} from {string} as {string}")
    public void searchEquipmentWithNameFrom(String equipmentType, String equipmentName, String studyNodeName, String alias) {
        ctx.getEquipmentType(equipmentType);
        ctx.searchEquipment(equipmentType, equipmentName, studyNodeName, alias, "NAME");
    }

    // --------------------------------------------------------
    @When("search equipment with name {string} as {string}")
    public void searchGenericEquipmentWithName(String equipmentName, String alias) {
        searchGenericEquipmentWithNameFrom(equipmentName, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @When("search equipment with name {string} from {string} as {string}")
    public void searchGenericEquipmentWithNameFrom(String equipmentName, String studyNodeName, String alias) {
        ctx.searchEquipment("", equipmentName, studyNodeName, alias, "NAME");
    }

    // --------------------------------------------------------
    @When("search {string} equipment with id {string} as {string}")
    public void searchEquipmentWithIdFrom(String equipmentType, String equipmentName, String alias) {
        searchEquipmentWithIdFrom(equipmentType, equipmentName, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @When("open sld on voltage-level {string} as {string}")
    public void openSld(String vlName, String alias) {
        openSldFrom(vlName, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @When("open sld on voltage-level {string} from {string} as {string}")
    public void openSldFrom(String vlName, String studyNodeName, String alias) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        JsonNode data = StudyRequests.getInstance().getSld(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, vlName);
        assertNotNull("no SLD data found", data);

        // this action includes some basic testing
        assertTrue("no svg found in SLD", data.has("svg"));
        assertTrue("no metadata found in SLD", data.has("metadata"));
        assertTrue("no additionalMetadata found in SLD", data.has("additionalMetadata"));
        assertTrue("svg is not XML", data.get("svg").asText().startsWith("<?xml"));
        JsonNode metadata = data.get("additionalMetadata");
        assertTrue("vl not found as metadata 'id'", metadata.has("id") && metadata.get("id").asText().equalsIgnoreCase(vlName));

        ctx.setData(alias, data);
    }

    // --------------------------------------------------------
    @When("open nad on voltage-levels {string} as {string}")
    public void openNad(String vlName, String alias) {
        openNadFrom(vlName, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @When("open nad on voltage-levels {string} from {string} as {string}")
    public void openNadFrom(String vlNames, String studyNodeName, String alias) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        List<String> vlList = List.of(vlNames.split(","));
        JsonNode data = StudyRequests.getInstance().getNad(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, vlList);
        assertNotNull("no SLD data found", data);

        // this action includes some basic testing
        assertTrue("no svg found in SLD", data.has("svg"));
        assertTrue("no additionalMetadata found in SLD", data.has("additionalMetadata"));
        assertTrue("svg is not XML", data.get("svg").asText().startsWith("<?xml"));
        JsonNode metadata = data.get("additionalMetadata");
        assertTrue("no voltageLevels found in metadata", metadata.has("voltageLevels"));
        JsonNode voltageLevels = metadata.get("voltageLevels");
        assertTrue("voltageLevels is not expected array", voltageLevels.isArray() && voltageLevels.size() >= vlList.size());
        List<String> vlIds = new ArrayList<>();
        for (JsonNode oneVl : voltageLevels) {
            if (oneVl.has("id")) {
                vlIds.add(oneVl.get("id").asText());
            }
        }
        for (String vl : vlList) {
            assertTrue("a voltageLevel is missing from additionalMetadata", vlIds.contains(vl));
        }
        ctx.setData(alias, data);
    }


    // --------------------------------------------------------
    @When("search {string} equipment with id {string} from {string} as {string}")
    public void searchEquipmentWithIdFrom(String equipmentType, String equipmentName, String studyNodeName, String alias) {
        ctx.getEquipmentType(equipmentType);
        ctx.searchEquipment(equipmentType, equipmentName, studyNodeName, alias, "ID");
    }

    @When("search equipment with id {string} as {string}")
    public void searchGenericEquipmentWithId(String equipmentName, String alias) {
        searchGenericEquipmentWithIdFrom(equipmentName, TestContext.CURRENT_ELEMENT, alias);
    }

    @When("search equipment with id {string} from {string} as {string}")
    public void searchGenericEquipmentWithIdFrom(String equipmentName, String studyNodeName, String alias) {
        ctx.searchEquipment("", equipmentName, studyNodeName, alias, "ID");
    }

    // --------------------------------------------------------
    @Then("todo")
    public void todo() {
        throw new io.cucumber.java.PendingException();
    }

    // --------------------------------------------------------
    @Then("sum {string} from {string} as {string}")
    public void sumValuesAs(String attribute, String valuesIdList, String alias) {
        String attrExpr = "/" + attribute;
        if (attribute.contains(".")) {
            attrExpr = "/" + attribute.replace('.', '/');
        }
        List<String> vList = List.of(valuesIdList.split(","));
        double sum = 0.0;
        for (String vId : vList) {
            JsonNode jsonRoot = ctx.getJsonData(vId);
            JsonNode jsonData = jsonRoot.at(attrExpr);
            if (jsonData.isValueNode()) {
                sum += jsonData.asDouble();
            }
        }
        // create a json data to store the result
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        result.put(attribute, sum);
        ctx.setData(alias, result);
    }

    // --------------------------------------------------------
    @Then("{string} values format {string} equal to")
    public void valuesFormatEqualTo(String valuesId, String format, DataTable expectedValues) {
        /*   expectedLineValues example:
        |  keyname | keyvalue  |
        |    id1   |  value1   |
        |    id2   |  value2   |
        ...
         */
        JsonNode jsonRoot = ctx.getJsonData(valuesId);

        // from our json data, build a table to compare with the expected one

        // format can be "exact" (string compare) or a decimal format pattern, like "0.00"
        DecimalFormat df = null;
        if (!format.equalsIgnoreCase("exact")) {
            // use fixed locale to always have the same decimal separator (US => .)
            df = new DecimalFormat(format, new DecimalFormatSymbols(Locale.US));
        }

        List<List<String>> computedLineValues = new ArrayList<>();
        computedLineValues.add(expectedValues.asLists().get(0));  // same header (|  keyname | keyvalue |)
        // get all key values of the expected table, skipping the header line (id1, id2, ...)
        Set<String> requestedAttributes = expectedValues.subTable(1, 0).asMap().keySet();

        for (String lineAttr : requestedAttributes) {
            // a key can be a json path "a.b.c" => "/a/b/c"
            String attrExpr = "/" + lineAttr;
            if (lineAttr.contains(".")) {
                attrExpr = "/" + lineAttr.replace('.', '/');
            }
            JsonNode jsonData = jsonRoot.at(attrExpr);
            if (jsonData.isValueNode()) {
                List<String> row = new ArrayList<>();
                row.add(lineAttr);
                if (df != null) {
                    // round half-even decimal value
                    row.add(df.format(jsonData.asDouble()));
                } else {
                    // exact text value
                    row.add(jsonData.asText());
                }
                computedLineValues.add(row);
            }
        }

        expectedValues.diff(DataTable.create(computedLineValues));
    }

    // --------------------------------------------------------
    @When("close switch {string}")
    public void closeSwitch(String switchId) {
        closeSwitchFrom(switchId, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("close switch {string} from {string}")
    public void closeSwitchFrom(String switchId, String studyNodeName) {
        updateSwitch(switchId, studyNodeName, false);
    }

    // --------------------------------------------------------
    @When("open switch {string}")
    public void openSwitch(String switchId) {
        openSwitchFrom(switchId, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("open switch {string} from {string}")
    public void openSwitchFrom(String switchId, String studyNodeName) {
        updateSwitch(switchId, studyNodeName, true);
    }

    // --------------------------------------------------------
    public void updateSwitch(String switchId, String studyNodeName, boolean openState) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        StudyRequests.getInstance().updateSwitch(switchId, nodeIds.studyId, nodeIds.nodeId, openState);
    }

    // --------------------------------------------------------
    @When("run loadflow")
    public void runLoadflow() {
        runLoadflowFrom(TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("run loadflow with limit reduction {int}")
    public void runLoadflowWithLimitReduction(int limitReduction) {
        runLoadflowFromWithLimitReduction(TestContext.CURRENT_ELEMENT, limitReduction);
    }

    // --------------------------------------------------------
    @When("run loadflow from {string}")
    public void runLoadflowFrom(String studyNodeName) {
        runLoadflowFromWithLimitReduction(studyNodeName, ctx.getIntParameter("limitReduction", 80));
    }

    // --------------------------------------------------------
    @When("run loadflow from {string} with limit reduction {int}")
    public void runLoadflowFromWithLimitReduction(String studyNodeName, int limitReduction) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        assertTrue("Node is not built", isNodeBuilt(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId));
        StudyRequests.getInstance().runLoadFlow(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, limitReduction);
    }

    // --------------------------------------------------------
    @Then("loadflow status is {string}")
    public void loadflowStatusIs(String computationStatus) {
        loadflowStatusIsFrom(computationStatus, TestContext.CURRENT_ELEMENT);
    }

    public void executeAndWaitForLoadFlowSuccess() {
        TestContext.Node node = ctx.getNodeId(TestContext.CURRENT_ELEMENT);
        NotificationWaiter.executeAndWaitForLoadFlowSuccess(this::runLoadflow, node, MAX_COMPUTATION_WAITING_TIME_IN_SEC);
    }

    // --------------------------------------------------------
    @Then("loadflow status is {string} from {string}")
    public void loadflowStatusIsFrom(String computationStatus, String studyNodeName) {
        // check LF completion is equal to expected computation status
        boolean statusMatching = ctx.waitForStatusMatching(computationStatus, studyNodeName, TestContext.Computation.LOADFLOW, MAX_COMPUTATION_WAITING_TIME_IN_SEC);
        assertTrue("Loadflow did not changed to status " + computationStatus, statusMatching);
    }

    // --------------------------------------------------------
    @When("get loadflow result as {string}")
    public void getLoadflowDataAs(String dataAlias) {
        getLoadflowDataFromAs(TestContext.CURRENT_ELEMENT, dataAlias);
    }

    // --------------------------------------------------------
    @When("get loadflow result from {string} as {string}")
    public void getLoadflowDataFromAs(String studyNodeName, String dataAlias) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        JsonNode lfData = StudyRequests.getInstance().getLoadFlowResult(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId);
        assertNotNull("Cannot retrieve loadflow result", lfData);
        ctx.setData(dataAlias, lfData);
    }

    // --------------------------------------------------------
    private void setComputationParametersWith(String resourceFileName, String computationName) {
        String studyId = ctx.getStudyId(TestContext.CURRENT_ELEMENT);
        String fileContent = Utils.getResourceFileContent(resourceFileName);
        StudyRequests.getInstance().setComputationParameters(studyId, computationName, fileContent);
    }

    // --------------------------------------------------------
    @When("set loadflow parameters with resource {string} with provider {string}")
    public void setLoadflowParametersWithResourceWithProvider(String resourceFileName, String provider) {
        setComputationParametersWith(resourceFileName, "loadflow");
        usingLoadflowOn(provider, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("get limit violations as {string}")
    public void getLimitViolationsAs(String dataAlias) {
        getLimitViolationsFromAs(TestContext.CURRENT_ELEMENT, dataAlias);
    }

    // --------------------------------------------------------
    @When("get limit violations from {string} as {string}")
    public void getLimitViolationsFromAs(String studyNodeName, String dataAlias) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        JsonNode violationsData = StudyRequests.getInstance().getLimitViolationsResult(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId);
        assertNotNull("Cannot retrieve limit violations result", violationsData);
        ctx.setData(dataAlias, violationsData);
    }

    // --------------------------------------------------------
    @When("get security-analysis result as {string}")
    public void getSecurityAnalysisResultAs(String dataAlias) {
        getSecurityAnalysisResultFromAs(TestContext.CURRENT_ELEMENT, dataAlias);
    }

    // --------------------------------------------------------
    @When("get security-analysis result from {string} as {string}")
    public void getSecurityAnalysisResultFromAs(String studyNodeName, String dataAlias) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode data = mapper.createObjectNode();

        // N (preContingencyResult)
        JsonNode saNData = StudyRequests.getInstance().getSecurityAnalysisResult(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, "N");
        assertNotNull("Cannot retrieve security-analysis result", saNData);
        if (saNData.isArray() && !saNData.isEmpty()) {
            data.set("N", saNData);
        } else {
            data.set("N", mapper.createArrayNode());
        }

        // N-K CONTINGENCIES
        JsonNode saNmKContingencies = StudyRequests.getInstance().getSecurityAnalysisResult(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, "NMK_CONTINGENCIES");
        assertNotNull("Cannot retrieve security-analysis result", saNmKContingencies);
        assertTrue("data 'NMK_CONTINGENCIES' does not contain 'content' child data", saNmKContingencies.has("content"));
        JsonNode saNmKContingenciesContent = saNmKContingencies.get("content");
        if (saNmKContingenciesContent.isArray() && !saNmKContingenciesContent.isEmpty()) {
            data.set("NMK_CONTINGENCIES_FIRST_PAGE", saNmKContingenciesContent);
            assertTrue("data 'NMK_CONTINGENCIES' does not contain 'totalElements' child data", saNmKContingencies.has("totalElements"));
            data.set("NMK_CONTINGENCIES_SIZE", saNmKContingencies.get("totalElements"));
        } else {
            data.set("NMK_CONTINGENCIES_FIRST_PAGE", mapper.createArrayNode());
            data.set("NMK_CONTINGENCIES_SIZE", mapper.createObjectNode());
        }

        // N-K CONSTRAINTS
        JsonNode saNmKLimitViolations = StudyRequests.getInstance().getSecurityAnalysisResult(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, "NMK_LIMIT_VIOLATIONS");
        assertNotNull("Cannot retrieve security-analysis result", saNmKLimitViolations);
        assertTrue("data 'NMK_LIMIT_VIOLATIONS' does not contain 'content' child data", saNmKLimitViolations.has("content"));
        JsonNode saNmKLimitViolationsContent = saNmKLimitViolations.get("content");
        if (saNmKLimitViolationsContent.isArray() && !saNmKLimitViolationsContent.isEmpty()) {
            data.set("NMK_LIMIT_VIOLATIONS_FIRST_PAGE", saNmKLimitViolationsContent);
            assertTrue("data 'NMK_LIMIT_VIOLATIONS' does not contain 'totalElements' child data", saNmKLimitViolations.has("totalElements"));
            data.set("NMK_LIMIT_VIOLATIONS_SIZE", saNmKLimitViolations.get("totalElements"));
        } else {
            data.set("NMK_LIMIT_VIOLATIONS_FIRST_PAGE", mapper.createArrayNode());
            data.set("NMK_LIMIT_VIOLATIONS_SIZE", mapper.createObjectNode());
        }

        ctx.setData(dataAlias, data);
        LOGGER.info("getSecurityAnalysisResultFromFromAs data '{}'", data);
    }

    // --------------------------------------------------------
    @When("trip line {string} of voltage level {string}")
    public void tripLine(String lineName, String voltageLevelName) {
        tripLineFrom(lineName, voltageLevelName, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("trip line {string} of voltage level {string} from {string}")
    public void tripLineFrom(String lineName, String voltageLevelName, String studyNodeName) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("action", "TRIP");
        body.put("energizedVoltageLevelId", voltageLevelName);
        body.put("equipmentId", lineName);
        body.put("type", "OPERATING_STATUS_MODIFICATION");
        // Rest call
        StudyRequests.getInstance().createModification(nodeIds.studyId, nodeIds.nodeId, body.toString());
    }

    // --------------------------------------------------------
    @Then("{string} values equal {string} values format {string}")
    public void valuesEqualValuesFormat(String values1Id, String values2Id, String format) {
        // no attr list specified => check all attrs
        ctx.valuesEqualityList(values1Id, values2Id, format, "all", true);
    }

    // --------------------------------------------------------
    @Then("{string} values not equal {string} values format {string}")
    public void valuesNotEqualValuesFormat(String values1Id, String values2Id, String format) {
        // no attr list specified => check all attrs
        ctx.valuesEqualityList(values1Id, values2Id, format, "all", false);
    }

    // --------------------------------------------------------
    @Then("{string} values not equal {string} values format {string} list {string}")
    public void valuesNotEqualValuesFormatList(String values1Id, String values2Id, String format, String attrList) {
        ctx.valuesEqualityList(values1Id, values2Id, format, attrList, false);
    }

    // --------------------------------------------------------
    @Then("{string} values equal {string} values format {string} list {string}")
    public void valuesEqualValuesFormatList(String values1Id, String values2Id, String format, String attrList) {
        ctx.valuesEqualityList(values1Id, values2Id, format, attrList, true);
    }

    // --------------------------------------------------------
    @When("set parameter {string} to {int}")
    public void setParameterTo(String paramName, int paramIntValue) {
        ConfigRequests.getInstance().setParameter(paramName, paramIntValue);
        ctx.setIntParameter(paramName, paramIntValue);
    }

    // --------------------------------------------------------
    @Given("using loadflow {string}")
    public void usingLoadflow(String provider) {
        usingLoadflowOn(provider, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @Given("using loadflow {string} on {string}")
    public void usingLoadflowOn(String provider, String studyName) {
        assertTrue("Provider must be in " + TestContext.LOADFLOW_PROVIDERS, TestContext.LOADFLOW_PROVIDERS.contains(provider));
        String studyId = ctx.getStudyId(studyName);
        StudyRequests.getInstance().setLoadFlowProvider(provider, studyId);
    }

    // --------------------------------------------------------
    @Given("contingency-list {string} exist in {string}")
    public void contingencyListExistIn(String listName, String directoryName) {
        ctx.elementExists(listName, "CONTINGENCY_LIST", directoryName, true);
    }

    // --------------------------------------------------------
    @Given("filter {string} exist in {string}")
    public void filterExistIn(String filterName, String directoryName) {
        ctx.elementExists(filterName, "FILTER", directoryName, true);
    }

    // --------------------------------------------------------
    @When("get contingency-list {string} from {string}")
    public void getContingencyListFrom(String listName, String directoryName) {
        String id = ctx.getElementFrom(listName, "CONTINGENCY_LIST", directoryName);
        ctx.setCurrentContingencyList(listName, id);
    }

    // --------------------------------------------------------
    @When("get contingency-list {string} from {string} as {string}")
    public void getContingencyListFromAs(String listName, String directoryName, String alias) {
        String id = ctx.getElementFrom(listName, "CONTINGENCY_LIST", directoryName);
        ctx.setCurrentContingencyList(alias, id);
    }

    // --------------------------------------------------------
    @When("get filter {string} from {string}")
    public void getFilterFrom(String filterName, String directoryName) {
        String id = ctx.getElementFrom(filterName, "FILTER", directoryName);
        ctx.setCurrentFilter(filterName, id);
    }

    // --------------------------------------------------------
    @When("get filter {string} from {string} as {string}")
    public void getFilterFromAs(String filterName, String directoryName, String alias) {
        String id = ctx.getElementFrom(filterName, "FILTER", directoryName);
        ctx.setCurrentFilter(alias, id);
    }

    // --------------------------------------------------------
    @Given("get case {string} from {string}")
    public void getCaseFrom(String caseName, String directoryName) {
        getCaseFromAs(caseName, directoryName, null);
    }

    // --------------------------------------------------------
    @Given("get case {string} from {string} as {string}")
    public void getCaseFromAs(String caseName, String directoryName, String alias) {
        String caseId = ctx.getElementFrom(caseName, "CASE", directoryName);
        ctx.setCurrentCase(alias != null ? alias : caseName, caseId);
        ctx.setCaseExtentions(caseName, getCaseExtensions(caseId));
    }

    // --------------------------------------------------------
    @When("select contingency-list {string} as {string}")
    public void selectcontingencyListAs(String listName, String alias) {
        selectContingencyListFromAs(listName, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @When("select contingency-list {string} from {string} as {string}")
    public void selectContingencyListFromAs(String listName, String studyNodeName, String alias) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        String listId = ctx.getContingencyListId(listName);

        JsonNode data = StudyRequests.getInstance().contingencyCount(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, listId);
        assertNotNull("no data found while selecting list " + listName, data);
        ctx.setData(alias, data);
    }

    // --------------------------------------------------------
    @When("run security-analysis with {string}")
    public void runSecurityAnalysisWith(String listName) {
        runSecurityAnalysisWithFrom(listName, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("run security-analysis with {string} from {string}")
    public void runSecurityAnalysisWithFrom(String listName, String studyNodeName) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        assertTrue("Node is not built", isNodeBuilt(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId));
        String listId = ctx.getContingencyListId(listName);
        StudyRequests.getInstance().runSecurityAnalysis(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, listId);
    }

    // --------------------------------------------------------
    @When("set security-analysis parameters with resource {string}")
    public void setSecurityAnalysisParametersWith(String resourceFileName) {
        setComputationParametersWith(resourceFileName, "security-analysis");
    }

    // --------------------------------------------------------
    @Then("security-analysis status is {string}")
    public void securityAnalysisStatusIs(String computationStatus) {
        securityAnalysisStatusIsFrom(computationStatus, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @Then("security-analysis status is {string} from {string}")
    public void securityAnalysisStatusIsFrom(String computationStatus, String studyNodeName) {
        securityAnalysisStatusIsFromTimeout(computationStatus, studyNodeName, MAX_COMPUTATION_WAITING_TIME_IN_SEC);
    }

    // --------------------------------------------------------
    @Then("security-analysis status is {string} timeout {int}")
    public void securityAnalysisStatusIsTimeout(String computationStatus, int timeoutInSeconds) {
        securityAnalysisStatusIsFromTimeout(computationStatus, TestContext.CURRENT_ELEMENT, timeoutInSeconds);
    }

    // --------------------------------------------------------
    @Then("security-analysis status is {string} from {string} timeout {int}")
    public void securityAnalysisStatusIsFromTimeout(String computationStatus, String studyNodeName, int timeoutInSeconds) {
        // check AS completion is equal to expected computation status
        boolean statusMatching = ctx.waitForStatusMatching(computationStatus, studyNodeName, TestContext.Computation.SECURITY_ANALYSIS, timeoutInSeconds);
        assertTrue("SecurityAnalysis did not changed to status " + computationStatus, statusMatching);
    }

    // --------------------------------------------------------
    @When("delete {string} equipment with id {string} from {string}")
    public void deleteEquipmentWithNameFrom(String equipmentType, String equipmentId, String studyNodeName) {
        String eqptDeleteType = ctx.getEquipmentType(equipmentType);
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);

        StudyRequests.getInstance().deleteEquipment(nodeIds.studyId, nodeIds.nodeId, eqptDeleteType, equipmentId);
    }

    // --------------------------------------------------------
    @When("delete {string} equipments with ids {string} from {string}")
    public void deleteEquipmentsWithIdsFrom(String equipmentType, String equipmentIds, String studyNodeName) {
        List<String> eqptIds = Arrays.asList(equipmentIds.split(","));
        String eqptDeleteType = ctx.getEquipmentType(equipmentType);
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);

        eqptIds.forEach(e -> StudyRequests.getInstance().deleteEquipment(nodeIds.studyId, nodeIds.nodeId, eqptDeleteType, e.trim()));
    }

    // --------------------------------------------------------
    @When("get child {string} from {string} as {string}")
    public void getChildFromAs(String attrName, String jsonInputDataName, String jsonOutputDataName) {
        JsonNode jsonInputData = ctx.getJsonData(jsonInputDataName);

        JsonNode newData = null;
        assertTrue("Cant find attr '" + attrName + "' in data '" + jsonInputDataName, jsonInputData.has(attrName));
        if (jsonInputData.isArray() || jsonInputData.isObject()) {
            newData = jsonInputData.get(attrName);
        }
        assertNotNull("Cant find array/object child '" + attrName + "' in data '" + jsonInputDataName, newData);
        ctx.setData(jsonOutputDataName, newData);
    }

    // --------------------------------------------------------
    @When("get child {string} as number from {string} as {string}")
    public void getChildAsNumberFromAs(String attrName, String jsonInputDataName, String jsonOutputDataName) {
        JsonNode jsonInputData = ctx.getJsonData(jsonInputDataName);

        JsonNode newData = null;
        assertTrue("Cant find attr '" + attrName + "' in data '" + jsonInputDataName, jsonInputData.has(attrName));
        if (jsonInputData.isArray() || jsonInputData.isObject()) {
            newData = jsonInputData.get(attrName);
        }
        // if attribute is present but empty, set the result to 0
        if (newData != null && !newData.isNumber() && newData.isEmpty()) {
            newData = new IntNode(0);
        }
        assertTrue("Cant find array/object child '" + attrName + "' as a number in data '" + jsonInputDataName, newData != null && newData.isNumber());
        ctx.setData(jsonOutputDataName, newData);
    }

    // --------------------------------------------------------
    @Then("{string} type is {string}")
    public void typeIs(String jsonInputDataName, String expectedType) {
        assertTrue("Type must be in " + TestContext.JSON_DATA_TYPES, TestContext.JSON_DATA_TYPES.contains(expectedType));
        JsonNode jsonInputData = ctx.getJsonData(jsonInputDataName);

        String actualType = null;
        if (jsonInputData.isArray()) {
            actualType = "array";
        } else if (jsonInputData.isObject()) {
            actualType = "object";
        } else if (jsonInputData.isValueNode()) {
            actualType = "value";
        }
        assertEquals("Bad json type", expectedType, actualType);
    }

    // --------------------------------------------------------
    @When("get index {int} from {string} as {string}")
    public void getIndexFromAs(Integer index, String jsonInputDataName, String jsonOutputDataName) {
        JsonNode jsonInputData = ctx.getJsonData(jsonInputDataName);
        assertTrue("Json data '" + jsonInputDataName + "' is not an array", jsonInputData.isArray());
        int maxIdx = jsonInputData.size() - 1;
        assertTrue("out-of-range: Json data array '" + jsonInputDataName + "' max possible index is " + maxIdx, index <= maxIdx);
        ctx.setData(jsonOutputDataName, jsonInputData.get(index));
    }

    // --------------------------------------------------------
    @When("extract sort {string} from {string} as {string}")
    public void extractSortFromAs(String attributeName, String jsonInputDataName, String jsonOutputDataName) {
        extractAttributeValues(attributeName, jsonInputDataName, jsonOutputDataName, true);
    }

    // --------------------------------------------------------
    @When("extract {string} from {string} as {string}")
    public void extractFromAs(String attributeName, String jsonInputDataName, String jsonOutputDataName) {
        extractAttributeValues(attributeName, jsonInputDataName, jsonOutputDataName, false);
    }

    private void extractAttributeValues(String attributeName, String jsonInputDataName, String jsonOutputDataName, boolean sort) {
        JsonNode jsonInputData = ctx.getJsonData(jsonInputDataName);
        assertTrue("Not array type", jsonInputData.isArray());
        // note: we extract only strings
        List<String> attributeValues = new ArrayList<>();
        for (JsonNode node : jsonInputData) {
            if (node.has(attributeName)) {
                attributeValues.add(node.get(attributeName).asText());
            }
        }
        if (sort) {
            Collections.sort(attributeValues);
        }
        // build a json object with a single property, whose value is the json values list "[v1, v2, ...]"
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        result.putPOJO(attributeName, attributeValues);
        ctx.setData(jsonOutputDataName, result);
    }

    // --------------------------------------------------------
    @When("cut modification {int} from {string} to {string}")
    public void cutModification(int modificationRank, String sourceNodeName, String targetNodeName) {
        duplicateModification(modificationRank, sourceNodeName, targetNodeName, "MOVE");
    }

    // --------------------------------------------------------
    @When("copy modification {int} from {string} to {string}")
    public void copyModification(int modificationRank, String sourceNodeName, String targetNodeName) {
        duplicateModification(modificationRank, sourceNodeName, targetNodeName, "COPY");
    }

    // --------------------------------------------------------
    private void duplicateModification(int modificationRank, String sourceNodeName, String targetNodeName, String copyMode) {
        // modificationRank starts from 1 for Gherkin, but starts from 0 internally
        String studyId = ctx.getStudyId(TestContext.CURRENT_ELEMENT); // in current study
        String sourceNodeId = StudyRequests.getInstance().getNodeId(studyId, sourceNodeName);
        assertNotNull("No source tree node named " + sourceNodeName, sourceNodeId);
        String targetNodeId = StudyRequests.getInstance().getNodeId(studyId, targetNodeName);
        assertNotNull("No target tree node named " + targetNodeName, targetNodeId);

        // List of modifications in source node
        JsonNode modifications = StudyRequests.getInstance().getModifications(studyId, sourceNodeId);
        assertNotNull(modifications);
        assertTrue("modifications is not a list", modifications.isArray());
        assertFalse("modifications list is empty", modifications.isEmpty());
        assertTrue("modifications index " + modificationRank + " must be in [1.." + modifications.size() + "]", modificationRank >= 1 && modificationRank <= modifications.size());

        // rest call for duplication
        JsonNode selectedModification = modifications.get(modificationRank - 1);
        StudyRequests.getInstance().duplicateModification(studyId, sourceNodeId, targetNodeId, copyMode, selectedModification.get("uuid").asText());
    }

    // --------------------------------------------------------
    @When("edit modification {int} from resource {string}")
    private void editModificationFromResource(int modificationRank, String resourceFileName) {
        // modificationRank starts from 1 for Gherkin, but starts from 0 internally
        TestContext.Node nodeIds = ctx.getNodeId(TestContext.CURRENT_ELEMENT);
        // List of modifications in source node
        JsonNode modifications = StudyRequests.getInstance().getModifications(nodeIds.studyId, nodeIds.nodeId);
        assertNotNull(modifications);
        assertTrue("modifications is not a list", modifications.isArray());
        assertFalse("modifications list is empty", modifications.isEmpty());
        assertTrue("modifications index " + modificationRank + " must be in [1.." + modifications.size() + "]", modificationRank >= 1 && modificationRank <= modifications.size());

        // retrieve Json content to be passed as request body
        String fileContent = Utils.getResourceFileContent(resourceFileName);

        JsonNode selectedModification = modifications.get(modificationRank - 1);
        StudyRequests.getInstance().editModification(nodeIds.studyId, nodeIds.nodeId, selectedModification.get("uuid").asText(), fileContent);
    }

    // --------------------------------------------------------
    @When("create modification from resource {string}")
    public void createModificationFromResource(String resourceFileName) {
        createModificationFromFromResource(TestContext.CURRENT_ELEMENT, resourceFileName);
    }

    // --------------------------------------------------------
    @When("create modifications from resources {string}")
    public void createModificationsFromResources(String resourcesFileNames) {
        List<String> resources = Arrays.asList(resourcesFileNames.split(","));
        resources.forEach(r -> createModificationFromFromResource(TestContext.CURRENT_ELEMENT, r.trim()));
    }

    // --------------------------------------------------------
    @When("create modification from {string} from resource {string}")
    public void createModificationFromFromResource(String studyNodeName, String resourceFileName) {
        // TODO: if node is already built, wait after creation for the node to be built again ?
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        // retrieve Json content to be passed as request body
        String fileContent = Utils.getResourceFileContent(resourceFileName);
        // Rest call
        StudyRequests.getInstance().createModification(nodeIds.studyId, nodeIds.nodeId, fileContent);
    }

    // --------------------------------------------------------
    @When("create explicit-naming filter {string} in {string} with equipment-type {string} and equipmentIds {string}")
    public void createExplicitNamingFilterWithEquipmentTypeAndEquipmentIds(String filterName, String directoryName, String equipmentType, String equipmentIds) {
        String dirId = ctx.getDirId(directoryName);
        // retrieve Json content to be passed as request body
        String fileContent = Utils.getResourceFileContent("data/request_models/model_explicit_naming_filter.json");
        // replace in the model
        String identifiableType = ctx.getEquipmentType(equipmentType);
        fileContent = fileContent.replace("%EQUIPMENT_TYPE%", identifiableType);
        ArrayNode equipmentIdsArray = new ObjectMapper().createArrayNode();
        Arrays.asList(equipmentIds.split(",")).forEach(e -> {
            ObjectNode equipmentId = new ObjectMapper().createObjectNode();
            equipmentId.put("equipmentID", e.trim());
            equipmentIdsArray.add(equipmentId);
        });
        fileContent = fileContent.replace("%FILTER_EQUIPMENTS_ATTRIBUTES%", equipmentIdsArray.toString());
        // Rest call
        ExploreRequests.getInstance().createFormFilter(filterName, "", dirId, fileContent);
        String listId = ctx.waitForElementCreation(dirId, "FILTER", filterName);
        assertNotNull("Filter not created in directory with name " + filterName, listId);
        ctx.setCurrentFilter(filterName, listId);
    }

    // --------------------------------------------------------
    @When("add property {string} to substation {string} with value {string}")
    public void addPropertyToWithValue(String propertyName, String substationId, String propertyValue) {
        addPropertyToWithValueIn(propertyName, substationId, propertyValue, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("add property {string} to substations {string} with value {string}")
    public void addPropertyToSubstationsWithValue(String propertyName, String substationIds, String propertyValue) {
        List<String> substationIdsList = Arrays.asList(substationIds.split(","));
        substationIdsList.forEach(s -> addPropertyToWithValueIn(propertyName, s.trim(), propertyValue, TestContext.CURRENT_ELEMENT));
    }

    private void addPropertyToWithValueIn(String propertyName, String substationId, String propertyValue, String currentElement) {
        TestContext.Node nodeIds = ctx.getNodeId(currentElement);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("type", "SUBSTATION_MODIFICATION");
        body.put("equipmentId", substationId);
        ObjectNode property = mapper.createObjectNode();
        property.put("name", propertyName);
        property.put("value", propertyValue);
        property.put("deletionMark", false);
        property.put("added", true);
        ArrayNode properties = mapper.createArrayNode();
        properties.add(property);
        body.set("properties", properties);
        // Rest call
        StudyRequests.getInstance().createModification(nodeIds.studyId, nodeIds.nodeId, body.toString());
    }

    // --------------------------------------------------------
    @When("create substation {string}")
    public void createSubstation(String substationId) {
        TestContext.Node nodeIds = ctx.getNodeId(TestContext.CURRENT_ELEMENT);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("type", "SUBSTATION_CREATION");
        body.put("equipmentId", substationId);
        // Rest call
        StudyRequests.getInstance().createModification(nodeIds.studyId, nodeIds.nodeId, body.toString());
    }

    // --------------------------------------------------------
    @When("set {string} to {string} for {string} with id {string}")
    public void setAttributeValueForEquipmentWithId(String attributeName, String attributeValue, String equipmentType, String equipmentId) {
        String modificationType = ctx.getModificationType(equipmentType);
        TestContext.Node nodeIds = ctx.getNodeId(TestContext.CURRENT_ELEMENT);
        // Rest call
        StudyRequests.getInstance().modifyEquipmentAttribute(nodeIds.studyId, nodeIds.nodeId, modificationType, equipmentId, attributeName, attributeValue);
    }

    // --------------------------------------------------------
    @When("set {string} to {string} for {string} with ids {string}")
    public void setAttributeValueForEquipmentsWithIds(String attributeName, String attributeValue, String equipmentType, String equipmentIds) {
        List<String> eqptIds = Arrays.asList(equipmentIds.split(","));
        String modificationType = ctx.getModificationType(equipmentType);
        TestContext.Node nodeIds = ctx.getNodeId(TestContext.CURRENT_ELEMENT);
        // Rest call
        eqptIds.forEach(e -> StudyRequests.getInstance().modifyEquipmentAttribute(nodeIds.studyId, nodeIds.nodeId, modificationType, e.trim(), attributeName, attributeValue));
    }

    // --------------------------------------------------------
    @When("delete node")
    public void deleteNode() {
        deleteNode(TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("delete node {string}")
    public void deleteNode(String studyNodeName) {
        ctx.deleteNode(studyNodeName, 200);
    }

    // --------------------------------------------------------
    @When("delete node catch {int}")
    public void deleteNodeCatch(int expectedHttpCode) {
        deleteNodeCatch(TestContext.CURRENT_ELEMENT, expectedHttpCode);
    }

    // --------------------------------------------------------
    @When("delete node {string} catch {int}")
    public void deleteNodeCatch(String studyNodeName, int expectedHttpCode) {
        ctx.deleteNode(studyNodeName, expectedHttpCode);
    }

    // --------------------------------------------------------
    @Then("node {string} does not exist")
    public void nodeDoesNotExist(String studyNodeName) {
        nodeFromDoesNotExist(studyNodeName, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @Then("node {string} from {string} does not exist")
    public void nodeFromDoesNotExist(String studyNodeName, String studyName) {
        String studyId = ctx.getStudyId(studyName);
        String nodeId = StudyRequests.getInstance().getNodeId(studyId, studyNodeName);
        assertNull("Should not have tree node named " + studyNodeName, nodeId);
    }

    // --------------------------------------------------------
    @When("create child node {string}")
    public void createChildNode(String newNodeName) {
        createChildNodeFrom(newNodeName, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("create child node {string} from {string}")
    public void createChildNodeFrom(String newNodeName, String studyNodeName) {
        createNode(newNodeName, studyNodeName, "CHILD");
    }

    // --------------------------------------------------------
    @When("create after node {string}")
    public void createAfterNode(String newNodeName) {
        createAfterNodeFrom(newNodeName, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("create after node {string} from {string}")
    public void createAfterNodeFrom(String newNodeName, String studyNodeName) {
        createNode(newNodeName, studyNodeName, "AFTER");
    }

    // --------------------------------------------------------
    @When("create before node {string}")
    public void createBeforeNode(String newNodeName) {
        createBeforeNodeFrom(newNodeName, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("create before node {string} from {string}")
    public void createBeforeNodeFrom(String newNodeName, String studyNodeName) {
        createNode(newNodeName, studyNodeName, "BEFORE");
    }

    // --------------------------------------------------------
    @When("rename node {string} to {string}")
    public void renameNode(String studyNodeName, String newNodeName) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        StudyRequests.getInstance().renameNode(nodeIds.studyId, nodeIds.nodeId, newNodeName);
        ctx.setCurrentNode(newNodeName, nodeIds.nodeId, nodeIds.studyId);
    }

    // --------------------------------------------------------
    private void createNode(String newNodeName, String studyNodeName, String creationMode) {
        String newNodeId = ctx.createNode(newNodeName, studyNodeName, creationMode);
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        ctx.setCurrentNode(newNodeName, newNodeId, nodeIds.studyId);
    }

    // --------------------------------------------------------
    @When("create security sequence from {string}")
    public void createSecuritySequence(String studyNodeName) {
        String newNodeId = ctx.createSecuritySequence(studyNodeName);
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        ctx.setCurrentNode(null, newNodeId, nodeIds.studyId);
    }

    // --------------------------------------------------------
    @When("export case format {string} in file {string}")
    public void exportCaseFormatInFile(String format, String fileName) {
        exportCaseFormatFromZippedInFile(format, TestContext.CURRENT_ELEMENT, fileName);
    }

    // --------------------------------------------------------
    @When("export case format {string} from {string} in file {string}")
    public void exportCaseFormatFromZippedInFile(String format, String studyNodeName, String fileName) {
        JsonNode formats = StudyRequests.getInstance().exportFormatList();
        assertNotNull(formats);
        assertTrue("Format must be in " + formats, formats.toString().contains(format));
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        // create a unique name
        final Path outPath = FileSystems.getDefault().getPath(fileName + "_" + UUID.randomUUID());
        try {
            UUID exportUuid = NotificationWaiter.executeAndWaitForExportNetworkFinished(
                    () -> StudyRequests.getInstance().exportCase(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId, format, Paths.get(fileName).getFileName().toString()),
                    nodeIds.studyId,
                    MAX_EXPORT_WAITING_TIME_IN_SEC);
            assertNotNull("Export UUID should not be null", exportUuid);
            NetworkConversionRequests.getInstance().downloadExportFile(exportUuid, outPath);
            assertTrue("No export file found in " + outPath, Files.exists(outPath));

            if (format.equalsIgnoreCase("XIIDM")) {
                validateSingleFileEntry(outPath, ".xiidm", "<iidm:network", "IIDM XML tag");
            } else if (format.equalsIgnoreCase("JIIDM")) {
                validateSingleFileEntry(outPath, ".jiidm", "\"version\"", "version attr");
            } else if (format.equalsIgnoreCase("CGMES")) {
                validateMultipleXmlEntries(outPath);
            }
        } finally {
            try {
                Files.deleteIfExists(outPath);
            } catch (IOException e) {
                LOGGER.info("exportCaseFormatFromFromInFile cleanup error: '{}'", e.getMessage());
            }
        }
    }

    private void validateSingleFileEntry(Path outPath, String fileExtension, String expectedContent, String contentDescription) {
        try (ZipFile zipFile = new ZipFile(outPath.toString())) {
            var entries = Collections.list(zipFile.entries());
            assertEquals("Zip entries number", 1, entries.size());
            entries.forEach(entry -> {
                assertTrue("Zip entry does not have expected extension " + fileExtension + ": " + entry.getName(), entry.getName().endsWith(fileExtension) && entry.getSize() > 0);
                validateFileContent(zipFile, entry, outPath, expectedContent, contentDescription);
            });
        } catch (IOException e) {
            LOGGER.error("exportCaseFormatFromFromInFile Zip extraction Error '{}'", e.getMessage());
            fail("Zip extraction error");
        }
    }

    private void validateMultipleXmlEntries(Path outPath) {
        try (ZipFile zipFile = new ZipFile(outPath.toString())) {
            var entries = Collections.list(zipFile.entries());
            assertEquals("Zip entries number", 4, entries.size());
            entries.forEach(entry -> {
                assertTrue("Zip entry is not XML: " + entry.getName(), entry.getName().endsWith(".xml") && entry.getSize() > 0);
            });
        } catch (IOException e) {
            LOGGER.error("Error reading content from zip entry '{}'", e.getMessage());
            fail("Zip extraction error");
        }
    }

    private void validateFileContent(ZipFile zipFile, ZipEntry entry, Path outPath, String expectedContent, String contentDescription) {
        try (InputStream inputStream = zipFile.getInputStream(entry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String firstLine = reader.readLine();
            String secondLine = reader.readLine();
            assertTrue("No export content in " + outPath, firstLine != null && !firstLine.isEmpty());
            String fileContent = firstLine + (secondLine != null ? secondLine : "");
            assertTrue("No " + contentDescription + " found in " + outPath, fileContent.contains(expectedContent));
        } catch (IOException e) {
            LOGGER.error("Error reading content from zip entry: {}", e.getMessage());
            fail("Error reading content from zip entry");
        }
    }

    // --------------------------------------------------------
    @When("create filter-contingency-list {string} in {string} with filter {string} from resource {string}")
    public void createFilterContingencyListInFromResource(String elementName, String directoryName, String filterName, String resourceFileName) {
        String dirId = ctx.getDirId(directoryName);

        // retrieve Json content to be passed as request body
        AtomicReference<String> fileContentRef = new AtomicReference<>(Utils.getResourceFileContent(resourceFileName));
        fileContentRef.set(fileContentRef.get().replaceFirst("%FILTER_ID%", ctx.getFilterId(filterName.trim())));

        // Rest call
        ExploreRequests.getInstance().createContingencyList(elementName, "", dirId, fileContentRef.get(), "filters");
        String listId = ctx.waitForElementCreation(dirId, "CONTINGENCY_LIST", elementName);
        assertNotNull("Contingency list not created in directory with name " + elementName + " whose content is " + fileContentRef.get(), listId);
        ctx.setCurrentContingencyList(elementName, listId);
    }

    // --------------------------------------------------------
    @When("create identifier-contingency-list {string} in {string} from resource {string} repeat {int}")
    public void createIdentifierContingencyListInFromResourceRepeat(String elementNamePrefix, String directoryName, String resourceFileName, int nbTimes) {
        for (int i = 1; i <= nbTimes; i++) {
            String eltName = elementNamePrefix + "_" + i;
            createIdentifierContingencyListInFromResource(eltName, directoryName, resourceFileName);
        }
    }

    // --------------------------------------------------------
    @When("create identifier-contingency-list {string} in {string} from resource {string}")
    public void createIdentifierContingencyListInFromResource(String elementName, String directoryName, String resourceFileName) {
        createContingencyListFromResource(elementName, directoryName, resourceFileName, "identifier");
    }

    // --------------------------------------------------------
    private void createContingencyListFromResource(String elementName, String directoryName, String resourceFileName, String listType) {
        String dirId = ctx.getDirId(directoryName);

        // retrieve Json content to be passed as request body
        String fileContent = Utils.getResourceFileContent(resourceFileName);

        // Rest call
        ExploreRequests.getInstance().createContingencyList(elementName, "", dirId, fileContent, listType);
        String listId = ctx.waitForElementCreation(dirId, "CONTINGENCY_LIST", elementName);
        assertNotNull("Contingency list not created in directory with name " + elementName, listId);
        ctx.setCurrentContingencyList(elementName, listId);
    }


    // --------------------------------------------------------
    @When("create form-filter {string} in {string} from resource {string}")
    public void createFormFilterInFromResource(String elementName, String directoryName, String resourceFileName) {
        String dirId = ctx.getDirId(directoryName);

        // retrieve Json content to be passed as request body
        String fileContent = Utils.getResourceFileContent(resourceFileName);

        // Rest call
        ExploreRequests.getInstance().createFormFilter(elementName, "", dirId, fileContent);
        String listId = ctx.waitForElementCreation(dirId, "FILTER", elementName);
        assertNotNull("Filter not created in directory with name " + elementName, listId);
        ctx.setCurrentFilter(elementName, listId);
    }

    // --------------------------------------------------------
    @Then("compare {string} violations from {string}")
    public void compareViolations(String violationType, String dataAlias, DataTable comparisonTable) {
        /*   comparisonTable example:
        | SELECT         | SIDE   | ATTRIBUTE  | MIN   |  MAX  |
        | BXLIEL41LUCON  | ONE    | value      | 640   |  650  |  ==> rowValue
        | BXLIEL41LUCON  | ONE    | overload   | 93    |  96   |
         */
        JsonNode jsonArray = ctx.getJsonData(dataAlias);
        assertTrue("data " + dataAlias + " is not a list", jsonArray.isArray());
        assertFalse("data " + dataAlias + " is empty", jsonArray.isEmpty());

        for (int lineNb = 1; lineNb < comparisonTable.height(); lineNb++) {
            List<String> rowValue = comparisonTable.row(lineNb);
            assertEquals("comparison table column number must be 5", 5, rowValue.size());

            String subjectId = rowValue.get(0);
            String side = rowValue.get(1);
            String attribute = rowValue.get(2);
            checkLimitViolationLine(jsonArray, subjectId, side, attribute, rowValue.get(3), rowValue.get(4), violationType, lineNb);
        }
    }

    // --------------------------------------------------------
    @Then("no {string} violations from {string}")
    public void noViolations(String violationType, String dataAlias, DataTable comparisonTable) {
        /*   comparisonTable example:
        | SELECT         | SIDE   |
        | BXLIEL41LUCON  | ONE    | ==> rowValue
        | BXLIEL41LUCON  | ONE    |
         */
        JsonNode jsonArray = ctx.getJsonData(dataAlias);
        assertTrue("data " + dataAlias + " is not a list", jsonArray.isArray());
        assertFalse("data " + dataAlias + " is empty", jsonArray.isEmpty());

        for (int lineNb = 1; lineNb < comparisonTable.height(); lineNb++) {
            List<String> rowValue = comparisonTable.row(lineNb);
            assertEquals("comparison table column number must be 2", 2, rowValue.size());
            String subjectId = rowValue.get(0);
            String side = rowValue.get(1);
            assertFalse(String.format("Line %d: subjectId %s present in '%s'", lineNb, subjectId, dataAlias),
                    Utils.existFromViolationsList(jsonArray, subjectId, side, violationType));
        }
    }

    // --------------------------------------------------------
    @Then("compare {string} contingencies from {string}")
    public void compareContingencies(String violationType, String dataAlias, DataTable comparisonTable) {
        /*   comparisonTable example:
        | CONTINGENCY              | SELECT         | SIDE  | ATTRIBUTE  | MIN  |  MAX  |
        | N-1 Beaulieu  Farradiere | BXLIEL41LUCON  | ONE   | value      | 700  |       |  => rowValue
         */
        JsonNode jsonObject = ctx.getJsonData(dataAlias);
        assertTrue("data " + dataAlias + " does not contain 'NMK_CONTINGENCIES_FIRST_PAGE' child data", jsonObject.has("NMK_CONTINGENCIES_FIRST_PAGE"));
        JsonNode jsonNkContingenciesArray = jsonObject.get("NMK_CONTINGENCIES_FIRST_PAGE");

        for (int lineNb = 1; lineNb < comparisonTable.height(); lineNb++) {
            List<String> rowValue = comparisonTable.row(lineNb);
            assertEquals("comparison table column number must be 6", 6, rowValue.size());

            String contingencyId = rowValue.get(0);
            JsonNode limitViolationsArray = Utils.getValueFromContingenciesList(jsonNkContingenciesArray, contingencyId);
            assertNotNull("Line " + lineNb + ": contingency not found in data", limitViolationsArray);
            String subjectId = rowValue.get(1);
            String side = rowValue.get(2);
            String attribute = rowValue.get(3);
            checkNmKContingenciesLimitViolationLine(limitViolationsArray, subjectId, side, attribute, rowValue.get(4), rowValue.get(5), violationType, lineNb);
        }
    }

    @Then("compare {string} limit violations from {string}")
    public void compareLimitViolations(String violationType, String dataAlias, DataTable comparisonTable) {
        /*   comparisonTable example:
        | LIMIT_VIOLATION    | SELECT                   | SIDE  | ATTRIBUTE  | MIN  |  MAX  |
        | BXLIEL41LUCON      | N-1 Beaulieu  Farradiere | ONE   | value      | 700  |       |  => rowValue
         */
        JsonNode jsonObject = ctx.getJsonData(dataAlias);
        assertTrue("data " + dataAlias + " does not contain 'NMK_LIMIT_VIOLATION_FIRST_PAGE' child data", jsonObject.has("NMK_LIMIT_VIOLATIONS_FIRST_PAGE"));
        JsonNode jsonNkContingenciesArray = jsonObject.get("NMK_LIMIT_VIOLATIONS_FIRST_PAGE");

        for (int lineNb = 1; lineNb < comparisonTable.height(); lineNb++) {
            List<String> rowValue = comparisonTable.row(lineNb);
            assertEquals("comparison table column number must be 6", 6, rowValue.size());

            String contingencyId = rowValue.get(0);
            JsonNode contingenciesArray = Utils.getValueFromLimitViolationsList(jsonNkContingenciesArray, contingencyId);
            assertNotNull("Line " + lineNb + ": contingency not found in data", contingenciesArray);
            String subjectId = rowValue.get(1);
            String side = rowValue.get(2);
            String attribute = rowValue.get(3);
            checkNmKLimitViolationsContingencyLine(contingenciesArray, subjectId, side, attribute, rowValue.get(4), rowValue.get(5), violationType, lineNb);
        }
    }

    private void checkNmKContingenciesLimitViolationLine(JsonNode jsonArray, String subjectId, String side, String attribute, String strMin, String strMax, String limitType, int lineNb) {
        /*
        Search in an array on object like:
            {
                "subjectId": "ALLE5L31ALLEM",
                "limitViolation": {
                    "limit": 597,
                    "limitName": "permanent",
                    "acceptableDuration": 2147483647,
                    "value": 801.2674731566298,
                    "side": "ONE",
                    "limitType": "CURRENT"
                }
            }
           Then try to retrieve 'attribute' property value, and check if in [min,max]
         */
        int min = Objects.isNull(strMin) ? Integer.MIN_VALUE : Integer.parseInt(strMin);
        int max = Objects.isNull(strMax) ? Integer.MAX_VALUE : Integer.parseInt(strMax);
        int intValue;

        String strValue = Utils.getValueFromContingencyViolationsList(jsonArray, subjectId, side, limitType, attribute);
        assertNotNull("Line " + lineNb + ": attribute not found in data", strValue);
        intValue = (int) Double.parseDouble(strValue);

        assertTrue(String.format("Line %d: value %d from data is not in [%s,%s]", lineNb, intValue,
                        min == Integer.MIN_VALUE ? "" : strMin, max == Integer.MAX_VALUE ? "" : strMax),
                intValue >= min && intValue <= max);
    }

    private void checkNmKLimitViolationsContingencyLine(JsonNode jsonArray, String contingencyId, String side, String attribute, String strMin, String strMax, String limitType, int lineNb) {
        /*
        Search in an array on object like:
            {
                "contingency": {
                    contingencyId: " N-1 Beaulieu  Farradiere",
                },
                "limitViolation": {
                    "limit": 597,
                    "limitName": "permanent",
                    "acceptableDuration": 2147483647,
                    "value": 801.2674731566298,
                    "side": "ONE",
                    "limitType": "CURRENT"
                }
            }
           Then try to retrieve 'attribute' property value, and check if in [min,max]
         */
        int min = Objects.isNull(strMin) ? Integer.MIN_VALUE : Integer.parseInt(strMin);
        int max = Objects.isNull(strMax) ? Integer.MAX_VALUE : Integer.parseInt(strMax);
        int intValue;

        String strValue = Utils.getValueFromLimitViolationContingenciesList(jsonArray, contingencyId, side, limitType, attribute);
        assertNotNull("Line " + lineNb + ": attribute not found in data", strValue);
        intValue = (int) Double.parseDouble(strValue);

        assertTrue(String.format("Line %d: value %d from data is not in [%s,%s]", lineNb, intValue,
                        min == Integer.MIN_VALUE ? "" : strMin, max == Integer.MAX_VALUE ? "" : strMax),
                intValue >= min && intValue <= max);
    }

    // --------------------------------------------------------
    @Then("check {string} equipments {string} exist")
    public void checkEquipmentsExist(String equipmentType, String equipmentIds) {
        List<String> eqptIds = Arrays.asList(equipmentIds.split(","));
        eqptIds.forEach(e -> {
            searchEquipmentWithIdFrom(equipmentType, e.trim(), e.trim());
            assertFalse(ctx.getJsonData(e.trim()).isEmpty());
        });
    }

    // --------------------------------------------------------
    @Then("check {string} equipments {string} do not exist")
    public void checkEquipmentsDoNotExist(String equipmentType, String equipmentIds) {
        List<String> eqptIds = Arrays.asList(equipmentIds.split(","));
        eqptIds.forEach(e -> {
            searchEquipmentWithIdFrom(equipmentType, e.trim(), e.trim());
            assertTrue(ctx.getJsonData(e.trim()).isEmpty());
        });
    }

    private void checkLimitViolationLine(JsonNode jsonArray, String subjectId, String side, String attribute, String strMin, String strMax, String limitType, int lineNb) {
        /*
        Search in an array on object like:
            {
                "subjectId": "ALLE5L31ALLEM",
                "limit": 597,
                "limitName": "permanent",
                "acceptableDuration": 2147483647,
                "value": 801.2674731566298,
                "side": "ONE",
                "limitType": "CURRENT"
            }
           Then try to retrieve 'attribute' property value, and check if in [min,max]
         */
        int min = Objects.isNull(strMin) ? Integer.MIN_VALUE : Integer.parseInt(strMin);
        int max = Objects.isNull(strMax) ? Integer.MAX_VALUE : Integer.parseInt(strMax);
        int intValue;
        if (attribute.equalsIgnoreCase("overload")) {
            // computed value from 'value' and 'limit'
            String value = Utils.getValueFromContingenciesList(jsonArray, subjectId, side, limitType, "value");
            assertNotNull("Line " + lineNb + ": 'value' not found in data", value);
            String limit = Utils.getValueFromContingenciesList(jsonArray, subjectId, side, limitType, "limit");
            assertNotNull("Line " + lineNb + ": 'limit' not found in data", limit);
            intValue = (int) (Double.parseDouble(value) / Double.parseDouble(limit) * 100.0);
        } else {
            String strValue = Utils.getValueFromContingenciesList(jsonArray, subjectId, side, limitType, attribute);
            assertNotNull("Line " + lineNb + ": attribute not found in data", strValue);
            intValue = (int) Double.parseDouble(strValue);
        }
        assertTrue(String.format("Line %d: value %d from data is not in [%s,%s]", lineNb, intValue,
                        min == Integer.MIN_VALUE ? "" : strMin, max == Integer.MAX_VALUE ? "" : strMax),
                intValue >= min && intValue <= max);
    }

    @When("edit modification {int} attribute {string} to {string}")
    public void editModificationAttributeTo(int modificationRank, String attributeName, String attributeValue) {
        // modificationRank starts from 1 for Gherkin, but starts from 0 internally
        TestContext.Node nodeIds = ctx.getNodeId(TestContext.CURRENT_ELEMENT);
        // List of modifications in source node
        JsonNode modifications = StudyRequests.getInstance().getModifications(nodeIds.studyId, nodeIds.nodeId);
        assertNotNull(modifications);
        assertTrue("modifications is not a list", modifications.isArray());
        assertFalse("modifications list is empty", modifications.isEmpty());
        assertTrue("modifications index " + modificationRank + " must be in [1.." + modifications.size() + "]", modificationRank >= 1 && modificationRank <= modifications.size());

        JsonNode selectedModification = modifications.get(modificationRank - 1);
        StudyRequests.getInstance().editModificationAttribute(nodeIds.studyId, nodeIds.nodeId, selectedModification.get("uuid").asText(), attributeName, attributeValue);
    }

    @When("run sensitivity-analysis")
    public void runSensitivityAnalysis() {
        runSensitivityFrom(TestContext.CURRENT_ELEMENT);
    }

    @When("run sensitivity-analysis from {string}")
    public void runSensitivityFrom(String studyNodeName) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        assertTrue("Node is not built", isNodeBuilt(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId));
        StudyRequests.getInstance().runSensitivityAnalysis(nodeIds.studyId, rootNetwork.rootNetworkUuid, nodeIds.nodeId);
    }

    // --------------------------------------------------------
    @Then("sensitivity-analysis status is {string} from {string}")
    public void sensitivityAnalysisStatusIsFrom(String computationStatus, String studyNodeName) {
        boolean statusMatching = ctx.waitForStatusMatching(computationStatus, studyNodeName, TestContext.Computation.SENSITIVITY_ANALYSIS, MAX_COMPUTATION_WAITING_TIME_IN_SEC);
        assertTrue("sensitivity analysis did not changed to status " + computationStatus, statusMatching);
    }

    @Then("sensitivity-analysis status is {string}")
    public void sensitivityAnalysisStatusIs(String computationStatus) {
        sensitivityAnalysisStatusIsFrom(computationStatus, TestContext.CURRENT_ELEMENT);

    }

    @When("set sensitivity-analysis parameters with provider {string} with filters {string} and with contingency {string} with resource {string}")
    public void setSensitivityAnalysisParametersWithFiltersAndWithContingencyWithResource(String provider, String filters, String contengency, String resourceFileName) {
        assertTrue("Provider must be in " + TestContext.SENSITIVITY_PROVIDERS, TestContext.SENSITIVITY_PROVIDERS.contains(provider));
        String studyId = ctx.getStudyId(TestContext.CURRENT_ELEMENT);
        AtomicReference<String> fileContentRef = new AtomicReference<>(Utils.getResourceFileContent(resourceFileName));
        fileContentRef.set(fileContentRef.get().replaceFirst("%PROVIDER%", provider));
        Arrays.stream(
                        filters.split(",")
                )
                .map(f -> ctx.getFilterId(f.trim()))
                .forEach(f -> fileContentRef.set(fileContentRef.get().replaceFirst("%FILTER_ID%", f)));
        Arrays.stream(
                        contengency.split(",")
                )
                .map(f -> ctx.getContingencyListId(f.trim()))
                .forEach(f -> fileContentRef.set(fileContentRef.get().replaceFirst("%CONTINGENCY_ID%", f)));
        StudyRequests.getInstance().setComputationParameters(studyId, "sensitivity-analysis", fileContentRef.get());
    }

    @When("get sensitivity-analysis report as {string}")
    public void getSensitivityAnalysisReportAs(String dataAlias) {
        getSensitivityAnalysisReportFromAs(TestContext.CURRENT_ELEMENT, dataAlias);
    }

    @When("get sensitivity-analysis report from {string} as {string}")
    public void getSensitivityAnalysisReportFromAs(String studyNodeName, String dataAlias) {
        getReportData(studyNodeName, dataAlias, TestContext.ReportType.SENSITIVITY_ANALYSIS.name(), true);
    }

    @When("get security-analysis report as {string}")
    public void getSecurityAnalysisReportAs(String dataAlias) {
        getSecurityAnalysisReportFromAs(TestContext.CURRENT_ELEMENT, dataAlias);
    }

    @When("get security-analysis report from {string} as {string}")
    public void getSecurityAnalysisReportFromAs(String studyNodeName, String dataAlias) {
        getReportData(studyNodeName, dataAlias, TestContext.ReportType.SECURITY_ANALYSIS.name(), true);
    }

    @When("get modification report as {string}")
    public void getModificationReportAs(String dataAlias) {
        getModificationReportFromAs(TestContext.CURRENT_ELEMENT, dataAlias);
    }

    @When("get modification report from {string} as {string}")
    public void getModificationReportFromAs(String studyNodeName, String dataAlias) {
        getReportData(studyNodeName, dataAlias, TestContext.ReportType.NETWORK_MODIFICATION.name(), true);
    }

    private void getReportData(String studyNodeName, String dataAlias, String reportType, boolean nodeOnlyReport) {
        TestContext.Node node = ctx.getNodeId(studyNodeName);
        TestContext.RootNetwork rootNetwork = ctx.getCurrentRootNetwork();
        JsonNode result = StudyRequests.getInstance().getParentNodesReport(node.studyId, rootNetwork.rootNetworkUuid, node.nodeId, reportType, nodeOnlyReport);
        assertNotNull("Cannot retrieve report result for " + reportType, result);
        ctx.setData(dataAlias, result);
    }

    @Then("{string} contains {} nodes")
    public void reportContainsNodes(String dataName, int nbNodes) {
        JsonNode jsonRoot = ctx.getJsonData(dataName);
        assertNotNull(jsonRoot);
        assertTrue("Report is not Array", jsonRoot.isArray());
        assertEquals("Bad nodes number", nbNodes, jsonRoot.size());
    }

    @Then("{string} contains sensitivity-analysis report")
    public void containsSensitivityAnalysisReport(String dataName) {
        checkComputationSubReport(dataName, TestContext.ReportType.SENSITIVITY_ANALYSIS.messageKey);
    }

    private void checkComputationSubReport(String dataName, String computationName) {
        // only 1 node in the report
        JsonNode nodeReport = ctx.getJsonData(dataName).get(0);
        JsonNode subReports = nodeReport.get("subReports");
        // Node should contain only one sub-report with the computation
        assertTrue("Cannot find single subReport", subReports != null && subReports.isArray() && subReports.size() == 1);
        // Ex: message = "SensitivityAnalysis (OpenLoadFlow)"
        assertTrue(subReports.get(0).get("message").asText().startsWith(computationName));
    }

    @When("get index {int} from {string} from {string} as {string}")
    public void getIndexFromFromAs(int index, String jsonInputDataName, String component, String jsonOutputDataName) {
        JsonNode jsonInputData = ctx.getJsonData(component);
        JsonNode results = jsonInputData.get(jsonInputDataName);
        assertTrue("Json data '" + jsonInputDataName + "' is not an array", results.isArray());
        int maxIdx = results.size() - 1;
        assertTrue("out-of-range: Json data array '" + results + "' max possible index is " + maxIdx, index <= maxIdx);
        ctx.setData(jsonOutputDataName, results.get(index));

    }

    @When("create {int} built nodes per branch in {int} branches from {string}")
    public void createBuiltNodesInBranches(int nbNodesPerBranch, int nbBranches, String rootNode) {
        createNodesInBranches(nbNodesPerBranch, nbBranches, rootNode, true);
    }

    @When("create {int} nodes per branch in {int} branches from {string}")
    public void createUnbuiltNodesInBranches(int nbNodesPerBranch, int nbBranches, String rootNode) {
        createNodesInBranches(nbNodesPerBranch, nbBranches, rootNode, false);
    }

    private void createNodesInBranches(int nbNodesPerBranch, int nbBranches, String rootNode, boolean build) {
        for (int b = 1; b <= nbBranches; b++) {
            getNode(rootNode);
            for (int n = 1; n <= nbNodesPerBranch; n++) {
                createChildNode(String.format("br-%d/n-%d", b, n));
                if (build) {
                    buildNode();
                }
            }
        }
    }
}
