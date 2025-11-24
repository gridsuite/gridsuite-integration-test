/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.gridsuite.bddtests.cases.CaseRequests;
import org.gridsuite.bddtests.common.EnvProperties;
import org.gridsuite.bddtests.common.TestContext;
import org.gridsuite.bddtests.common.Utils;
import org.gridsuite.bddtests.explore.ExploreRequests;
import org.gridsuite.bddtests.networkconversion.NetworkConversionRequests;
import org.gridsuite.bddtests.study.StudyRequests;
import org.junit.platform.commons.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.gridsuite.bddtests.common.TestContext.MAX_COMPUTATION_WAITING_TIME_IN_SEC;
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

    // used
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

    // used
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
    @When("get study {string} from {string}")
    public void getStudyFrom(String studyName, String directoryName) {
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
    private boolean isNodeBuilt(String studyId, String rootNetworkUuid, String nodeId) {
        String status = StudyRequests.getInstance().builtStatus(studyId, rootNetworkUuid, nodeId);
        assertNotNull("Could not get build status", status);
        return status.matches("BUILT|BUILT_WITH_ERROR|BUILT_WITH_WARNING");
    }

    // --------------------------------------------------------
    @When("get first root network from {string}")
    public void getFirstRootNetworkFrom(String studyNodeName) {
        String studyId = ctx.getStudyId(studyNodeName);
        String rootNetworkId = StudyRequests.getInstance().getFirstRootNetworkId(studyId);
        ctx.setCurrentRootNetworkUuid(rootNetworkId);
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

    // --------------------------------------------------------
    @Then("loadflow status is {string} from {string}")
    public void loadflowStatusIsFrom(String computationStatus, String studyNodeName) {
        // check LF completion is equal to expected computation status
        boolean statusMatching = ctx.waitForStatusMatching(computationStatus, studyNodeName, TestContext.Computation.LOADFLOW, MAX_COMPUTATION_WAITING_TIME_IN_SEC);
        assertTrue("Loadflow did not changed to status " + computationStatus, statusMatching);
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
}
