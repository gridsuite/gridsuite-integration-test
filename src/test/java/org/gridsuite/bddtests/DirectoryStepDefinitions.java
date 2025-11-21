/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.*;
import org.gridsuite.bddtests.common.EnvProperties;
import org.gridsuite.bddtests.common.TestContext;
import org.gridsuite.bddtests.directory.DirectoryRequests;
import org.gridsuite.bddtests.explore.ExploreRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class DirectoryStepDefinitions {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryStepDefinitions.class);

    private final TestContext ctx;

    // DI with PicoContainer to share the same context among all steps classes
    public DirectoryStepDefinitions(TestContext ctx) {
        this.ctx = ctx;
    }

    // --------------------------------------------------------
    @When("get directory data from {string} as {string}")
    public void getDirectoryDataFromAs(String directoryAliasName, String dataAliasName) {
        String dirId = ctx.getDirId(directoryAliasName);
        JsonNode data = DirectoryRequests.getInstance().getElementInfo(dirId);
        ctx.setData(dataAliasName, data);
    }

    // --------------------------------------------------------
    @When("create directory {string} in {string} owned by {string}")
    public void createDirectoryInOwnedBy(String dirName, String parentName, String owner) {
        ctx.createDirectory(dirName, parentName, owner);
    }

    // --------------------------------------------------------
    @When("create directory {string} in {string}")
    public void createDirectoryIn(String dirName, String parentName) {
        ctx.createDirectory(dirName, parentName, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @When("using tmp root directory as {string} owned by {string}")
    public void usingTmpRootDirectoryAsOwnedBy(String aliasName, String owner) {
        ctx.createTmpRootDirectoryAs(aliasName, owner, false);
    }

    // --------------------------------------------------------
    @Given("check or create directory {string} in {string}")
    public void checkOrCreateDirectory(String directoryName, String parentName) {
        String dataName = UUID.randomUUID().toString();
        getDirectoryContentFromAs(parentName, dataName);
        String existingDirId = hasElement(dataName, directoryName, "DIRECTORY");
        if (existingDirId == null) {
            createDirectoryIn(directoryName, parentName);
        } else {
            ctx.setCurrentDirectory(directoryName, existingDirId);
        }
    }

    // --------------------------------------------------------
    @When("get directory content from {string} as {string}")
    public void getDirectoryContentFromAs(String dirName, String dataName) {
        String dirId = ctx.getDirId(dirName);
        JsonNode dirData = DirectoryRequests.getInstance().getElements(EnvProperties.getInstance().getUserName(), dirId);
        assertNotNull("Cannot get directory content of " + dirName, dirData);
        ctx.setData(dataName, dirData);
    }

    // --------------------------------------------------------
    @Then("remove tmp directories from {string} older than {int} minutes")
    public void removeTmpDirectories(String tmpDirName, int expirationDurationMinutes) {
        String dirId = ctx.getDirId(tmpDirName);
        JsonNode elementListJson = DirectoryRequests.getInstance().getElements(EnvProperties.getInstance().getUserName(), dirId);
        assertNotNull("Cannot get directory content of " + tmpDirName, elementListJson);

        final String elementName = "elementName";
        final String elementType = "type";
        final String elementCreation = "creationDate";
        final String elementUuid = "elementUuid";
        final long expirationDurationSeconds = TimeUnit.MINUTES.toSeconds(expirationDurationMinutes);
        int counter = 0;
        for (JsonNode oneElement : elementListJson) {
            assertTrue("directory content must have " + elementName, oneElement.has(elementName));
            assertTrue("directory content must have " + elementType, oneElement.has(elementType));
            assertTrue("directory content must have " + elementCreation, oneElement.has(elementCreation));
            assertTrue("directory content must have " + elementUuid, oneElement.has(elementUuid));
            if (oneElement.get(elementType).asText().equalsIgnoreCase("DIRECTORY") && oneElement.get(elementName).asText().startsWith("bddtmp_")) {
                Instant creationInstant = Instant.parse(oneElement.get(elementCreation).asText()); // Can parse ISO 8601 format (ex: 2025-02-27T10:34:09.441839Z)
                Duration d = Duration.between(creationInstant, Instant.now());
                if (d.getSeconds() > expirationDurationSeconds) {
                    LOGGER.info("Remove old dir {} {} > {}", oneElement.get(elementName).asText(), d.getSeconds(), expirationDurationSeconds);
                    ExploreRequests.getInstance().removeElement(oneElement.get(elementUuid).asText(), EnvProperties.getInstance().getUserName());
                    counter++;
                }
            }
        }
        LOGGER.info("Removed: {}", counter);
    }

    // --------------------------------------------------------
    @When("create directories {string} in {string} depth {int}")
    public void createDirectoriesInOwnedByDepth(String dirPrefix, String parentName, int depth) {
        String pName = parentName;
        for (int i = 1; i <= depth; i++) {
            String subDirName = dirPrefix + "_" + i;
            ctx.createDirectory(subDirName, pName, EnvProperties.getInstance().getUserName());
            pName = subDirName;
        }
    }

    // --------------------------------------------------------
    @When("create directories {string} in {string} repeat {int}")
    public void createDirectoriesInRepeat(String dirPrefix, String parentName, int nbTimes) {
        for (int i = 1; i <= nbTimes; i++) {
            String subDirName = dirPrefix + "_" + i;
            ctx.createDirectory(subDirName, parentName, EnvProperties.getInstance().getUserName());
        }
    }

    // --------------------------------------------------------
    @When("create root directories {string} repeat {int}")
    public void createRootDirectoriesRepeat(String dirPrefix, int nbTimes) {
        for (int i = 1; i <= nbTimes; i++) {
            String rootDirName = dirPrefix + "_" + i;
            ctx.createRootDirectory(rootDirName, rootDirName, "", EnvProperties.getInstance().getUserName());
        }
    }

    // --------------------------------------------------------
    @Given("root directory {string} exists")
    @Given("get root directory {string}")
    public void rootDirectoryExists(String directoryName) {
        String dirId = DirectoryRequests.getInstance().getRootDirectoryId(EnvProperties.getInstance().getUserName(), directoryName);
        assertNotNull("Cannot find root directory named " + directoryName, dirId);
        ctx.setCurrentDirectory(directoryName, dirId);
    }

    @Given("check or create root directory {string}")
    public void checkOrCreateRootDirectory(String directoryName) {
        ctx.checkOrCreateRootDirectory(directoryName);
    }

    // --------------------------------------------------------
    @Given("root directory {string} does not exist")
    public void rootDirectoryDoesNotExist(String directoryName) {
        String dirId = DirectoryRequests.getInstance().getRootDirectoryId(EnvProperties.getInstance().getUserName(), directoryName);
        assertNull("Should not find root directory named " + directoryName, dirId);
    }

    // --------------------------------------------------------
    @When("create root directory {string}")
    public void createRootDirectory(String dirName) {
        ctx.createRootDirectory(dirName, dirName, "", EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @Given("using tmp root directory as {string}")
    public void usingTmpRootDirectoryAs(String aliasName) {
        ctx.createTmpRootDirectoryAs(aliasName, EnvProperties.getInstance().getUserName(), false);
    }

    @Given("using tmp root directory as {string} noremove")
    // Debug purpose: noremove means we don't remove the tmp dir at the end of the test
    public void usingTmpRootDirectoryNoRemoveAs(String aliasName) {
        ctx.createTmpRootDirectoryAs(aliasName, EnvProperties.getInstance().getUserName(), true);
    }

    // --------------------------------------------------------
    @Given("using tmp directory as {string}")
    public void usingTmpDirectoryAs(String aliasName) {
        ctx.createTmpDirectoryAs(aliasName, EnvProperties.getInstance().getUserName(), false);
    }

    // --------------------------------------------------------
    @Given("using tmp directory prefixed {string} as {string}")
    public void usingTmpDirectoryAs(String tmpDirPrefix, String aliasName) {
        ctx.createTmpDirectoryAs(tmpDirPrefix, aliasName, EnvProperties.getInstance().getUserName(), false);
    }

    @Given("using tmp directory as {string} noremove")
    // Debug purpose: noremove means we don't remove the tmp dir at the end of the test
    public void usingTmpDirectoryNoRemoveAs(String aliasName) {
        ctx.createTmpDirectoryAs(aliasName, EnvProperties.getInstance().getUserName(), true);
    }

    // --------------------------------------------------------
    @When("remove directory {string}")
    public void removeDirectory(String dirName) {
        String dirId = ctx.getDirId(dirName);
        ExploreRequests.getInstance().removeElement(dirId, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @When("get subdirectory {string} from {string}")
    public void getSubdirectoryFrom(String dirName, String parentName) {
        String id = ctx.getElementFrom(dirName, "DIRECTORY", parentName);
        ctx.setCurrentDirectory(dirName, id);
    }

    // --------------------------------------------------------
    @When("rename directory {string} into {string}")
    public void renameDirectoryInto(String dirName, String newName) {
        String dirId = ctx.getDirId(dirName);
        DirectoryRequests.getInstance().renameElement(dirId, newName, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @When("rename contingency-list {string} into {string}")
    public void renameContingencyListInto(String eltAlias, String newName) {
        String eltId = ctx.getContingencyListId(eltAlias);
        DirectoryRequests.getInstance().renameElement(eltId, newName, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @When("rename filter {string} into {string}")
    public void renameFilterInto(String eltAlias, String newName) {
        String eltId = ctx.getFilterId(eltAlias);
        DirectoryRequests.getInstance().renameElement(eltId, newName, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @When("rename study {string} into {string}")
    public void renameStudyInto(String eltAlias, String newName) {
        String eltId = ctx.getStudyId(eltAlias);
        DirectoryRequests.getInstance().renameElement(eltId, newName, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @When("remove contingency-list {string}")
    public void removeContingencyList(String aliasName) {
        String eltId = ctx.getContingencyListId(aliasName);
        ExploreRequests.getInstance().removeElement(eltId, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @When("remove filter {string}")
    public void removeFilter(String aliasName) {
        String eltId = ctx.getFilterId(aliasName);
        ExploreRequests.getInstance().removeElement(eltId, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @When("remove study {string}")
    public void removeStudy(String aliasName) {
        String eltId = ctx.getStudyId(aliasName);
        ExploreRequests.getInstance().removeElement(eltId, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @When("remove case {string}")
    public void removeCase(String aliasName) {
        String eltId = ctx.getCaseId(aliasName);
        ExploreRequests.getInstance().removeElement(eltId, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @Then("{string} cardinality is {int}")
    public void cardinalityIs(String dataName, int cardNumber) {
        JsonNode jsonRoot = ctx.getJsonData(dataName);
        assertEquals("data size error", cardNumber, jsonRoot.size());
    }

    // --------------------------------------------------------
    @Then("{string} cardinality is more than {int}")
    public void cardinalityIsMoreThan(String dataName, int minCardNumber) {
        JsonNode jsonRoot = ctx.getJsonData(dataName);
        assertTrue("data min size error", jsonRoot.size() > minCardNumber);
    }

    // --------------------------------------------------------
    @Then("{string} does not contain element {string}")
    public void doesNotContainElement(String dataName, String eltName) {
        hasElement(dataName, eltName, null, false);
    }

    // --------------------------------------------------------
    @Then("{string} contains element {string}")
    public void containsElement(String dataName, String eltName) {
        hasElement(dataName, eltName, null, true);
    }

    // --------------------------------------------------------
    @Then("{string} contains directory {string}")
    public void containsDirectory(String dataName, String eltName) {
        hasElement(dataName, eltName, "DIRECTORY", true);
    }

    // --------------------------------------------------------
    @Then("{string} does not contain directory {string}")
    public void doesNotContainDirectory(String dataName, String eltName) {
        hasElement(dataName, eltName, "DIRECTORY", false);
    }

    // --------------------------------------------------------
    @Then("{string} contains case {string}")
    public void containsCase(String dataName, String eltName) {
        hasElement(dataName, eltName, "CASE", true);
    }

    // --------------------------------------------------------
    @Then("{string} does not contain case {string}")
    public void doesNotContainCase(String dataName, String eltName) {
        hasElement(dataName, eltName, "CASE", false);
    }

    // --------------------------------------------------------
    @Then("{string} contains study {string}")
    public void containsStudy(String dataName, String eltName) {
        hasElement(dataName, eltName, "STUDY", true);
    }

    // --------------------------------------------------------
    @Then("{string} does not contain study {string}")
    public void doesNotContainStudy(String dataName, String eltName) {
        hasElement(dataName, eltName, "STUDY", false);
    }

    // --------------------------------------------------------
    @Then("{string} contains contingency-list {string}")
    public void containsContingencyList(String dataName, String eltName) {
        hasElement(dataName, eltName, "CONTINGENCY_LIST", true);
    }

    // --------------------------------------------------------
    @Then("{string} does not contain contingency-list {string}")
    public void doesNotContainsContingencyList(String dataName, String eltName) {
        hasElement(dataName, eltName, "CONTINGENCY_LIST", false);
    }

    // --------------------------------------------------------
    @Then("{string} contains filter {string}")
    public void containsFilter(String dataName, String eltName) {
        hasElement(dataName, eltName, "FILTER", true);
    }

    // --------------------------------------------------------
    @Then("{string} does not contain filter {string}")
    public void doesNotContainFilter(String dataName, String eltName) {
        hasElement(dataName, eltName, "FILTER", false);
    }

    // --------------------------------------------------------
    private void hasElement(String dataName, String eltName, String eltType, boolean shouldExist) {
        // data is supposed to be a directory content (array)
        JsonNode jsonDirArray = ctx.getJsonData(dataName);
        assertTrue("directory content must be an array", jsonDirArray.isArray());

        // json attr names
        final String elementName = "elementName";
        final String elementType = "type";

        boolean found = false;
        for (JsonNode oneElement : jsonDirArray) {
            assertTrue("directory content must have " + elementName, oneElement.has(elementName));
            if (eltType != null) {
                assertTrue("directory content must have " + elementType, oneElement.has(elementType));
            }
            if (eltName.equalsIgnoreCase(oneElement.get(elementName).asText()) && (eltType == null || eltType.equalsIgnoreCase(oneElement.get(elementType).asText()))) {
                found = true;
            }
        }
        if (shouldExist) {
            assertTrue("element should exist: " + eltName, found);
        } else {
            assertFalse("element should not exist: " + eltName, found);
        }
    }

    // --------------------------------------------------------
    private String hasElement(String dataName, String eltName, String eltType) {
        // data is supposed to be a directory content (array)
        JsonNode jsonDirArray = ctx.getJsonData(dataName);
        if (!jsonDirArray.isArray()) {
            return null;
        }
        // json attr names
        final String elementName = "elementName";
        final String elementType = "type";
        final String elementUuid = "elementUuid";
        for (JsonNode oneElement : jsonDirArray) {
            if (oneElement.has(elementName) && oneElement.has(elementType) && oneElement.has(elementUuid) &&
                    eltName.equalsIgnoreCase(oneElement.get(elementName).asText()) &&
                    (eltType == null || eltType.equalsIgnoreCase(oneElement.get(elementType).asText()))) {
                return oneElement.get(elementUuid).asText();
            }
        }
        return null;
    }

    // --------------------------------------------------------
    @Then("directory {string} is present and owned by {string}")
    public void directoryIsPresentAndOwnedBy(String dirName, String user) {
        ctx.directoryPresence(dirName);
        ctx.elementOwner(dirName, user);
    }

    // --------------------------------------------------------
    @When("move filter {string} into {string}")
    public void moveFilterInto(String eltName, String targetDirName) {
        ctx.moveElement(List.of(ctx.getFilterId(eltName)), targetDirName, 200);
    }

    // --------------------------------------------------------
    @When("move contingency-lists {string} into {string}")
    public void moveContingencyListInto(String eltsName, String targetDirName) {
        List<String> eltsId = Stream.of(eltsName.split(","))
                .map(String::trim)
                .map(ctx::getContingencyListId)
                .collect(Collectors.toList());
        ctx.moveElement(eltsId, targetDirName, 200);
    }

    // --------------------------------------------------------
    @When("move contingency-list {string} into {string} catch {int}")
    public void moveContingencyListIntoCatch(String eltsName, String targetDirName, int expectedHttpCode) {
        List<String> eltsId = Stream.of(eltsName.split(","))
                .map(String::trim)
                .map(ctx::getContingencyListId)
                .collect(Collectors.toList());
        ctx.moveElement(eltsId, targetDirName, expectedHttpCode);
    }

    // --------------------------------------------------------
    @Then("exit {string}")
    public void exit(String msg) {
        // Debug action: to exit a scenario somewhere before the end
        fail("exit " + msg);
    }

    // --------------------------------------------------------
    @Then("{string} equal to {int}")
    public void equalTo(String dataName, int cardNumber) {
        JsonNode jsonRoot = ctx.getJsonData(dataName);
        assertTrue("not a number", jsonRoot.isNumber());
        int intValue = ctx.getJsonData(dataName).intValue();
        assertEquals(cardNumber, intValue);
    }
}
