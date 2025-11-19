/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import org.gridsuite.bddtests.actions.ActionsRequests;
import org.gridsuite.bddtests.cases.CaseRequests;
import org.gridsuite.bddtests.common.TestContext;
import org.gridsuite.bddtests.common.Utils;
import org.gridsuite.bddtests.common.EnvProperties;
import org.gridsuite.bddtests.explore.ExploreRequests;
import org.gridsuite.bddtests.study.StudyRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.*;

import static org.junit.Assert.*;

public class StudySrvStepDefinitions {

    private final TestContext ctx;

    // DI with PicoContainer to share the same context among all steps classes
    public StudySrvStepDefinitions(TestContext ctx) {
        this.ctx = ctx;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(StudySrvStepDefinitions.class);

    // --------------------------------------------------------
    // setup before each scenario  (before BACKGROUND) - should be only here => UNIQUE for all steps for all feature files
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
    // BACKGROUND condition

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

    // --------------------------------------------------------
    @Given("case {string} does not exist in {string}")
    public void caseDoesNotExistIn(String caseName, String directoryName) {
        ctx.elementExists(caseName, "CASE", directoryName, false);
    }

    // --------------------------------------------------------
    @Given("case {string} exists in {string}")
    public void caseExistIn(String caseName, String directoryName) {
        String caseId = ctx.elementExists(caseName, "CASE", directoryName, true);
        ctx.setCase(caseName, caseId);
    }

    // --------------------------------------------------------
    @Then("wait for {string} case creation in {string}")
    public void waitForCaseCreationIn(String caseName, String directoryName) {
        String dirId = ctx.getDirId(directoryName);

        // check element creation in target directory
        String caseId = ctx.waitForElementCreation(dirId, "CASE", caseName);
        assertNotNull("Case not created in directory with name " + caseName, caseId);

        // check case creation completion
        final String cId = caseId;
        RetryPolicy<Object> retryPolicyStudy = RetryPolicy.builder()
                .withDelay(Duration.ofMillis(1000))
                .withMaxRetries(TestContext.MAX_WAITING_TIME_IN_SEC)
                .onRetriesExceeded(e -> LOGGER.warn("Waiting time exceeded"))
                .handleResult(Boolean.FALSE)
                .build();
        LOGGER.info("Wait for '{}' case creation completion (max: {} sec)", caseName, retryPolicyStudy.getConfig().getMaxRetries());
        boolean studyExists = Failsafe.with(retryPolicyStudy).get(() -> CaseRequests.getInstance().existsCase(cId));
        assertTrue("Case full creation not confirmed", studyExists);

        ctx.setCase(caseName, caseId);
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
        ExploreRequests.getInstance().createCaseFromFile(caseName, resourceFile, description, dirId, user);
    }

    // --------------------------------------------------------
    @When("create study {string} in {string} from resource {string}")
    public void createStudyInDirectoryFromResource(String studyName, String directoryName, String caseFileName) {
        String dirId = ctx.getDirId(directoryName);
        final String description = "STEP create_study_in_directory_from_resource";

        // just look into resources dir !
        Path resourceFile = Paths.get("src", "test", "resources", caseFileName);
        assertTrue("Cannot find resource file named " + resourceFile.toFile().getAbsolutePath(),
            Files.exists(resourceFile) && Files.isRegularFile(resourceFile));

        String user = EnvProperties.getInstance().getUserName();
        // async request:
        ExploreRequests.getInstance().createStudyFromFile(studyName, resourceFile, description, dirId, user);
    }

    // --------------------------------------------------------
    @When("create study {string} in {string} from {string} file {string}")
    public void createStudyInDirectoryFromFile(String studyName, String directoryName, String subDir, String fileName) {
        String dirId = ctx.getDirId(directoryName);
        final String description = "STEP create_study_in_directory_from_file";

        // look into local filesystem in $BDD_DATA_DIR/<subDir> directory
        Path bddFile = Utils.getBddFile(subDir, fileName);

        String user = EnvProperties.getInstance().getUserName();
        // async request:
        ExploreRequests.getInstance().createStudyFromFile(studyName, bddFile, description, dirId, user);
    }

    // --------------------------------------------------------
    @When("create study {string} in {string} from case {string}")
    public void createStudyInDirectoryFromCase(String studyName, String directoryName, String caseName) {
        String dirId = ctx.getDirId(directoryName);
        String caseId = ctx.getCaseId(caseName);
        final String description = "STEP create_study_in_directory_from_case";
        String user = EnvProperties.getInstance().getUserName();
        // async request:
        ExploreRequests.getInstance().createStudyFromCase(studyName, caseId, description, dirId, user);
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
    @Then("wait for {string} study creation in {string} timeout {int}")
    public void waitForStudyCreationIn(String studyName, String directoryName, int secondsTimeout) {
        ctx.waitForStudyCreation(studyName, directoryName, secondsTimeout);

    }

    // --------------------------------------------------------
    @Then("wait for {string} study creation in {string}")
    public void waitForStudyCreationIn(String studyName, String directoryName) {
        ctx.waitForStudyCreation(studyName, directoryName, TestContext.MAX_WAITING_TIME_IN_SEC);
    }

    // --------------------------------------------------------
    @When("get study {string} from {string}")
    public void getStudyFrom(String studyName, String directoryName) {
        String id = ctx.getElementFrom(studyName, "STUDY", directoryName);
        ctx.setCurrentStudy(id);
    }

    // --------------------------------------------------------
    // getStudy with an alias (so we can open/access N studies)
    @When("get study {string} from {string} as {string}")
    public void getStudyFromAs(String studyName, String directoryName, String alias) {
        String id = ctx.getElementFrom(studyName, "STUDY", directoryName);
        ctx.setStudy(alias, id);
    }

    // --------------------------------------------------------
    @When("get node {string}")
    public void getNode(String studyNodeName) {
        // by default we use the current/last study Id
        getNodeFromAs(studyNodeName, TestContext.CURRENT_ELEMENT, null);
    }

    // --------------------------------------------------------
    @When("get node {string} as {string}")
    public void getNodeAs(String studyNodeName, String alias) {
        // by default we use the current/last study Id
        getNodeFromAs(studyNodeName, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @When("get node {string} from {string}")
    public void getNodeFrom(String studyNodeName, String studyName) {
        getNodeFromAs(studyNodeName, studyName, null);
    }

    // --------------------------------------------------------
    @When("get node {string} from {string} as {string}")
    public void getNodeFromAs(String studyNodeName, String studyName, String alias) {
        String studyId = ctx.getStudyId(studyName);
        // check existence + keep Id only
        String nodeId = StudyRequests.getInstance().getNodeId(studyId, studyNodeName);
        assertNotNull("No current tree node named " + studyNodeName, nodeId);
        if (alias != null) {
            ctx.setNode(alias, nodeId, studyId);
        } else {
            ctx.setCurrentNode(nodeId, studyId);
        }
    }

    // --------------------------------------------------------
    @When("get node data {string} as {string}")
    public void getNodeData(String studyNodeName, String alias) {
        getNodeDataFrom(studyNodeName, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @When("get node data {string} from {string} as {string}")
    public void getNodeDataFrom(String studyNodeName, String studyName, String alias) {
        String studyId = ctx.getStudyId(studyName);
        // check existence + keep all json data
        JsonNode nodeData = StudyRequests.getInstance().getNodeData(studyId, studyNodeName, "name");
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
        ctx.checkEqptType1(equipmentType);
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);

        boolean inUpstreamBuiltParentNode = false;  // Should search in upstream built node
        JsonNode data = StudyRequests.getInstance().getEquipmentData(nodeIds.studyId, nodeIds.nodeId, equipmentType, equipmentId, inUpstreamBuiltParentNode);
        assertNotNull("no data found for equipment " + equipmentType + " with ID " + equipmentId, data);
        ctx.setData(alias, data);
    }

    // --------------------------------------------------------
    @And("get {string} equipment {string} with substation {string} as {string}")
    public void getEquipmentWithSubstation(String equipmentType, String equipmentId, String substationId, String alias) {
        getEquipmentWithSubstationFrom(equipmentType, equipmentId, substationId, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @And("get {string} equipment {string} with substation {string} from {string} as {string}")
    public void getEquipmentWithSubstationFrom(String equipmentType, String equipmentId, String substationId, String studyNodeName, String alias) {
        ctx.checkEqptType2(equipmentType);
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);

        JsonNode data = StudyRequests.getInstance().getEquipmentData(nodeIds.studyId, nodeIds.nodeId, equipmentType, equipmentId, substationId);
        assertNotNull("no data found for equipment " + equipmentType + " with ID " + equipmentId, data);
        ctx.setData(alias, data);
    }

    // --------------------------------------------------------
    @When("search {string} equipment with name {string} as {string}")
    public void searchEquipmentWithName(String equipmentType, String equipmentName, String alias) {
        searchEquipmentWithNameFrom(equipmentType, equipmentName, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @When("search {string} equipment with name {string} from {string} as {string}")
    public void searchEquipmentWithNameFrom(String equipmentType, String equipmentName, String studyNodeName, String alias) {
        ctx.checkEqptType(equipmentType);
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
    @When("search {string} equipment with id {string} from {string} as {string}")
    public void searchEquipmentWithIdFrom(String equipmentType, String equipmentName, String studyNodeName, String alias) {
        ctx.checkEqptType(equipmentType);
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
    @And("run loadflow")
    public void runLoadflow() {
        runLoadflowFrom(TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @And("run loadflow from {string}")
    public void runLoadflowFrom(String studyNodeName) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        StudyRequests.getInstance().runLoadFlow(nodeIds.studyId, nodeIds.nodeId);
    }

    // --------------------------------------------------------
    @Then("wait for loadflow status {string}")
    public void waitForLoadflowStatus(String computationStatus) {
        waitForLoadflowStatusFrom(computationStatus, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @Then("wait for loadflow status {string} from {string}")
    public void waitForLoadflowStatusFrom(String computationStatus, String studyNodeName) {
        // check LF completion is equal to expected computation status
        boolean statusMatching = ctx.waitForStatusMatching(computationStatus, studyNodeName, TestContext.Computation.LOADFLOW, TestContext.MAX_COMPUTATION_WAITING_TIME_IN_SEC);
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
        String result = StudyRequests.getInstance().getLoadFlowInfos(nodeIds.studyId, nodeIds.nodeId);
        assertTrue("TODO getLoadFlowInfos does not return LF details anymore (just the status, like CONVERGED)", result.isEmpty());
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
        JsonNode saData = StudyRequests.getInstance().getSecurityAnalysisResult(nodeIds.studyId, nodeIds.nodeId);
        assertNotNull("Cannot retrieve security-analysis result", saData);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode data = mapper.createObjectNode();

        // N (preContingencyResult)
        JsonNode limitViolationsArray = saData.at("/preContingencyResult/limitViolationsResult/limitViolations");
        if (limitViolationsArray.isArray() && limitViolationsArray.size() > 0) {
            data.set("N", limitViolationsArray);
        } else {
            data.set("N", mapper.createArrayNode());
        }

        // N-K (postContingencyResults)
        ArrayNode nkArray = mapper.createArrayNode();
        if (saData.hasNonNull("postContingencyResults")) {
            JsonNode p = saData.get("postContingencyResults");
            if (p.isArray()) {
                for (JsonNode oneResult : p) {
                    JsonNode violationResult = oneResult.at("/limitViolationsResult/limitViolations");
                    if (violationResult.isArray() && violationResult.size() > 0) {
                        nkArray.add(oneResult);
                    }
                }
            }
        }
        data.set("N-K", nkArray);

        ctx.setData(dataAlias, data);
        LOGGER.info("getSecurityAnalysisResultFromFromAs data '{}'", data);
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
        ctx.setContingencyList(listName, id);
    }

    // --------------------------------------------------------
    @When("get contingency-list {string} from {string} as {string}")
    public void getcontingencyListFromAs(String listName, String directoryName, String alias) {
        String id = ctx.getElementFrom(listName, "CONTINGENCY_LIST", directoryName);
        ctx.setContingencyList(alias, id);
    }

    // --------------------------------------------------------
    @When("get filter {string} from {string}")
    public void getFilterFrom(String filterName, String directoryName) {
        String id = ctx.getElementFrom(filterName, "FILTER", directoryName);
        ctx.setFilter(filterName, id);
    }

    // --------------------------------------------------------
    @When("get filter {string} from {string} as {string}")
    public void getFilterFromAs(String filterName, String directoryName, String alias) {
        String id = ctx.getElementFrom(filterName, "FILTER", directoryName);
        ctx.setFilter(alias, id);
    }

    // --------------------------------------------------------
    @Given("get case {string} from {string} as {string}")
    public void getCaseFromAs(String caseName, String directoryName, String alias) {
        String id = ctx.getElementFrom(caseName, "CASE", directoryName);
        ctx.setCase(alias, id);
    }

    // --------------------------------------------------------
    @When("select contingency-list {string} as {string}")
    public void selectcontingencyListAs(String listName, String alias) {
        selectcontingencyListFromAs(listName, TestContext.CURRENT_ELEMENT, alias);
    }

    // --------------------------------------------------------
    @When("select contingency-list {string} from {string} as {string}")
    public void selectcontingencyListFromAs(String listName, String studyNodeName, String alias) {
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);
        String listId = ctx.getContingencyListId(listName);

        JsonNode data = StudyRequests.getInstance().contingencyCount(nodeIds.studyId, nodeIds.nodeId, listId);
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
        String listId = ctx.getContingencyListId(listName);
        StudyRequests.getInstance().runSecurityAnalysis(nodeIds.studyId, nodeIds.nodeId, listId);
    }

    // --------------------------------------------------------
    @Then("wait for security-analysis status {string}")
    public void waitForSecurityAnalysisStatus(String computationStatus) {
        waitForSecurityAnalysisStatusFrom(computationStatus, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @Then("wait for security-analysis status {string} from {string}")
    public void waitForSecurityAnalysisStatusFrom(String computationStatus, String studyNodeName) {
        waitForSecurityAnalysisStatusFromTimeout(computationStatus, studyNodeName, TestContext.MAX_COMPUTATION_WAITING_TIME_IN_SEC);
    }

    // --------------------------------------------------------
    @Then("wait for security-analysis status {string} timeout {int}")
    public void waitForSecurityAnalysisStatusTimeout(String computationStatus, int timeoutInSeconds) {
        waitForSecurityAnalysisStatusFromTimeout(computationStatus, TestContext.CURRENT_ELEMENT, timeoutInSeconds);
    }

    // --------------------------------------------------------
    @Then("wait for security-analysis status {string} from {string} timeout {int}")
    public void waitForSecurityAnalysisStatusFromTimeout(String computationStatus, String studyNodeName, int timeoutInSeconds) {
        // check AS completion is equal to expected computation status
        boolean statusMatching = ctx.waitForStatusMatching(computationStatus, studyNodeName, TestContext.Computation.SECURITY_ANALYSIS, timeoutInSeconds);
        assertTrue("SecurityAnalysis did not changed to status " + computationStatus, statusMatching);
    }

    // --------------------------------------------------------
    @When("delete {string} equipment with id {string} from {string}")
    public void deleteEquipmentWithNameFrom(String equipmentType, String equipmentId, String studyNodeName) {
        String eqptDeleteType = ctx.checkEqptType(equipmentType);
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);

        StudyRequests.getInstance().deleteEquipment(nodeIds.studyId, nodeIds.nodeId, eqptDeleteType, equipmentId);
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
    @Then("get index {int} from {string} as {string}")
    public void getIndexFromAs(Integer index, String jsonInputDataName, String jsonOutputDataName) {
        JsonNode jsonInputData = ctx.getJsonData(jsonInputDataName);
        assertTrue("Json data '" + jsonInputDataName + "' is not an array", jsonInputData.isArray());
        int maxIdx = jsonInputData.size() - 1;
        assertTrue("out-of-range: Json data array '" + jsonInputDataName + "' max possible index is " + maxIdx, index <= maxIdx);
        ctx.setData(jsonOutputDataName, jsonInputData.get(index));
    }

    // --------------------------------------------------------
    @When("create {string} equipment from resource {string}")
    public void createEquipmentFromResource(String equipmentType, String resourceFileName) {
        createEquipmentFromFromResource(equipmentType, TestContext.CURRENT_ELEMENT, resourceFileName);
    }

    // --------------------------------------------------------
    @When("create {string} equipment from {string} from resource {string}")
    public void createEquipmentFromFromResource(String equipmentType, String studyNodeName, String resourceFileName) {
        ctx.upsertEquipment(equipmentType, studyNodeName, resourceFileName, true);
    }

    // --------------------------------------------------------
    @When("modify {string} equipment from resource {string}")
    public void modifyEquipmentFromResource(String equipmentType, String resourceFileName) {
        modifyEquipmentFromFromResource(equipmentType, TestContext.CURRENT_ELEMENT, resourceFileName);
    }

    // --------------------------------------------------------
    @When("modify {string} equipment from {string} from resource {string}")
    public void modifyEquipmentFromFromResource(String equipmentType, String studyNodeName, String resourceFileName) {
        ctx.upsertEquipment(equipmentType, studyNodeName, resourceFileName, false);
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
        ctx.createNode(newNodeName, studyNodeName, "CHILD");
    }

    // --------------------------------------------------------
    @When("create after node {string}")
    public void createAfterNode(String newNodeName) {
        createAfterNodeFrom(newNodeName, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("create after node {string} from {string}")
    public void createAfterNodeFrom(String newNodeName, String studyNodeName) {
        ctx.createNode(newNodeName, studyNodeName, "AFTER");
    }

    // --------------------------------------------------------
    @When("create before node {string}")
    public void createBeforeNode(String newNodeName) {
        createBeforeNodeFrom(newNodeName, TestContext.CURRENT_ELEMENT);
    }

    // --------------------------------------------------------
    @When("create before node {string} from {string}")
    public void createBeforeNodeFrom(String newNodeName, String studyNodeName) {
        ctx.createNode(newNodeName, studyNodeName, "BEFORE");
    }

    // --------------------------------------------------------
    @When("export case format {string} in file {string}")
    public void exportCaseFormatInFile(String format, String fileName) {
        exportCaseFormatFromInFile(format, TestContext.CURRENT_ELEMENT, fileName);
    }

    // --------------------------------------------------------
    @When("export case format {string} from {string} in file {string}")
    public void exportCaseFormatFromInFile(String format, String studyNodeName, String fileName) {
        JsonNode formats = StudyRequests.getInstance().exportFormatList();
        assertNotNull(formats);
        assertTrue("Format must be in " + formats, formats.toString().contains(format));
        TestContext.Node nodeIds = ctx.getNodeId(studyNodeName);

        // create a unique name
        final Path outPath = FileSystems.getDefault().getPath(fileName + "_" + UUID.randomUUID());

        try {
            StudyRequests.getInstance().exportCase(nodeIds.studyId, nodeIds.nodeId, format, outPath);

            // Note: this When step also performs the Then step (verifications)
            assertTrue("No export file found in " + outPath, Files.exists(outPath));

            if (format.equalsIgnoreCase("XIIDM")) {
                // search for iidm XML tag in the 2 first lines
                String fileContent = Utils.readFileContent(outPath, 2);
                assertTrue("No export content in " + outPath, fileContent != null && fileContent.length() > 0);
                assertTrue("No IIDM XML tag found in " + outPath, fileContent.contains("<iidm:network"));
            }
        } finally {
            try {
                Files.deleteIfExists(outPath);
            } catch (IOException e) {
                LOGGER.info("exportCaseFormatFromFromInFile Error '{}'", e.getMessage());
            }
        }
    }

    // --------------------------------------------------------
    @When("create script-filter {string} in {string}")
    public void createScriptFilterIn(String elementName, String directoryName) {
        ctx.createFilterIn(elementName, directoryName, "SCRIPT");
    }

    // --------------------------------------------------------
    @When("create form-filter {string} in {string}")
    public void createFormFilterIn(String elementName, String directoryName) {
        ctx.createFilterIn(elementName, directoryName, "FORM");
    }

    // --------------------------------------------------------
    @When("create form-contingency-list {string} in {string} from resource {string} repeat {int}")
    public void createFormContingencyListInFromResourceRepeat(String elementNamePrefix, String directoryName, String resourceFileName, int nbTimes) {
        for (int i = 1; i <= nbTimes; i++) {
            String eltName = elementNamePrefix + "_" + i;
            createFormContingencyListInFromResource(eltName, directoryName, resourceFileName);
        }
    }

    // --------------------------------------------------------
    @When("create form-contingency-list {string} in {string} from resource {string}")
    public void createFormContingencyListInFromResource(String elementName, String directoryName, String resourceFileName) {
        String dirId = ctx.getDirId(directoryName);
        String user = EnvProperties.getInstance().getUserName();

        // 1- create a default List
        ExploreRequests.getInstance().createFormContingencyList(elementName, "225kV spanish lines", dirId, user);
        // 2- retrieve the element
        String listId = ctx.waitForElementCreation(dirId, "CONTINGENCY_LIST", elementName);
        assertNotNull("Contingency list not created in directory with name " + elementName, listId);

        // 3- update it
        Path resourceFile = Paths.get("src", "test", "resources", resourceFileName);
        assertTrue("Cannot find resource file named " + resourceFile.toFile().getAbsolutePath(),
                Files.exists(resourceFile) && Files.isRegularFile(resourceFile));
        String fileContent = Utils.readFileContent(resourceFile, -1);
        assertTrue("Cannot read content from resource file named " + resourceFile.toFile().getAbsolutePath(), fileContent != null && fileContent.length() > 0);
        // use real uuid in resource file, replacing "<ID>"
        fileContent = fileContent.replace("\"<ID>\"", "\"" + listId + "\"");
        ActionsRequests.getInstance().updateFormContingencyList(listId, fileContent);

        ctx.setContingencyList(elementName, listId);
    }

    // --------------------------------------------------------
    @When("copy contingency-list {string} to script {string} in {string}")
    public void copyContingencyListToScriptIn(String formListName, String newScriptListName, String directoryName) {
        String dirId = ctx.getDirId(directoryName);
        String listId = ctx.getContingencyListId(formListName);
        String user = EnvProperties.getInstance().getUserName();
        ExploreRequests.getInstance().copyFormContingencyListAsScript(listId, newScriptListName, dirId, user);

        String newlistId = ctx.waitForElementCreation(dirId, "CONTINGENCY_LIST", newScriptListName);
        assertNotNull("Contingency list copy not created in directory with name " + newScriptListName, newlistId);
    }
}
