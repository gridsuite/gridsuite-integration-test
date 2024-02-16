/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.*;
import org.gridsuite.bddtests.common.EnvProperties;
import org.gridsuite.bddtests.common.TestContext;
import org.gridsuite.bddtests.directory.DirectoryRequests;
import org.gridsuite.bddtests.explore.ExploreRequests;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class DirectoryStepDefinitions {

    private final TestContext ctx;

    // DI with PicoContainer to share the same context among all steps classes
    //public DirectoryStepDefinitions(TestContext ctx) {
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
    @When("create private directory {string} in {string} owned by {string}")
    public void createPrivateDirectoryInOwnedBy(String dirName, String parentName, String owner) {
        ctx.createDirectory(dirName, parentName, true, owner);
    }

    // --------------------------------------------------------
    @When("create private directory {string} in {string}")
    public void createPrivateDirectoryIn(String dirName, String parentName) {
        ctx.createDirectory(dirName, parentName, true, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @When("create public directory {string} in {string} owned by {string}")
    public void createPublicDirectoryInOwnedBy(String dirName, String parentName, String owner) {
        ctx.createDirectory(dirName, parentName, false, owner);
    }

    // --------------------------------------------------------
    @When("create public directory {string} in {string}")
    public void createPublicDirectoryIn(String dirName, String parentName) {
        ctx.createDirectory(dirName, parentName, false, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @When("using tmp root directory as {string} owned by {string}")
    public void usingPrivateTmpRootDirectoryAsOwnedBy(String aliasName, String owner) {
        ctx.createTmpRootDirectoryAs(aliasName, owner, true);
    }

    // --------------------------------------------------------
    @When("using public tmp root directory as {string} owned by {string}")
    public void usingPublicTmpRootDirectoryAsOwnedBy(String aliasName, String owner) {
        ctx.createTmpRootDirectoryAs(aliasName, owner, false);
    }

    // --------------------------------------------------------
    @And("get directory content from {string} as {string}")
    public void getDirectoryContentFromAs(String dirName, String dataName) {
        String dirId = ctx.getDirId(dirName);
        JsonNode dirData = DirectoryRequests.getInstance().getElements(EnvProperties.getInstance().getUserName(), dirId);
        assertNotNull("Cannot get directory content of " + dirName, dirData);
        ctx.setData(dataName, dirData);
    }

    // --------------------------------------------------------
    @And("create public directories {string} in {string} depth {int}")
    public void createPublicDirectoriesInOwnedByDepth(String dirPrefix, String parentName, int depth) {
        String pName = parentName;
        for (int i = 1; i <= depth; i++) {
            String subDirName = dirPrefix + "_" + i;
            ctx.createDirectory(subDirName, pName, false, EnvProperties.getInstance().getUserName());
            pName = subDirName;
        }
    }

    // --------------------------------------------------------
    @When("create public directories {string} in {string} repeat {int}")
    public void createPublicDirectoriesInRepeat(String dirPrefix, String parentName, int nbTimes) {
        for (int i = 1; i <= nbTimes; i++) {
            String subDirName = dirPrefix + "_" + i;
            ctx.createDirectory(subDirName, parentName, false, EnvProperties.getInstance().getUserName());
        }
    }

    // --------------------------------------------------------
    @When("create root directories {string} repeat {int}")
    public void createRootDirectoriesRepeat(String dirPrefix, int nbTimes) {
        for (int i = 1; i <= nbTimes; i++) {
            String rootDirName = dirPrefix + "_" + i;
            ctx.createRootDirectory(rootDirName, rootDirName, false, "", EnvProperties.getInstance().getUserName());
        }
    }

    // --------------------------------------------------------
    @Given("root directory {string} exists")
    @Given("get root directory {string}")
    public void rootDirectoryExists(String directoryName) {
        String dirId = DirectoryRequests.getInstance().getRootDirectoryId(EnvProperties.getInstance().getUserName(), directoryName);
        assertNotNull("Cannot find root directory named " + directoryName, dirId);
        ctx.setDirectory(directoryName, dirId);
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
        ctx.createRootDirectory(dirName, dirName, false, "", EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @Given("using tmp root directory as {string}")
    public void usingPrivateTmpRootDirectoryAs(String aliasName) {
        ctx.createTmpRootDirectoryAs(aliasName, EnvProperties.getInstance().getUserName(), true);
    }

    // --------------------------------------------------------
    @Given("using public tmp root directory as {string}")
    public void usingPublicTmpRootDirectoryAs(String aliasName) {
        ctx.createTmpRootDirectoryAs(aliasName, EnvProperties.getInstance().getUserName(), false);
    }

    // --------------------------------------------------------
    @When("remove directory {string}")
    public void removeDirectory(String dirName) {
        String dirId = ctx.getDirId(dirName);
        ExploreRequests.getInstance().removeElement(dirId, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @And("get subdirectory {string} from {string}")
    public void getSubdirectoryFrom(String dirName, String parentName) {
        String id = ctx.getElementFrom(dirName, "DIRECTORY", parentName);
        ctx.setDirectory(dirName, id);
    }

    // --------------------------------------------------------
    @When("rename directory {string} into {string}")
    public void renameDirectoryInto(String dirName, String newName) {
        String dirId = ctx.getDirId(dirName);
        DirectoryRequests.getInstance().renameElement(dirId, newName, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @And("rename contingency-list {string} into {string}")
    public void renameContingencyListInto(String eltAlias, String newName) {
        String eltId = ctx.getContingencyListId(eltAlias);
        DirectoryRequests.getInstance().renameElement(eltId, newName, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @And("rename filter {string} into {string}")
    public void renameFilterInto(String eltAlias, String newName) {
        String eltId = ctx.getFilterId(eltAlias);
        DirectoryRequests.getInstance().renameElement(eltId, newName, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @When("update directory {string} with right {string}")
    public void updateDirectoryWithRight(String dirName, String newRight) {
        assertTrue("Right must be private or public, not " + newRight, newRight.equalsIgnoreCase("public") || newRight.equalsIgnoreCase("private"));
        boolean isPrivate = newRight.equalsIgnoreCase("private");
        String dirId = ctx.getDirId(dirName);
        DirectoryRequests.getInstance().updateDirectoryRights(dirId, isPrivate, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @And("remove contingency-list {string}")
    public void removeContingencyList(String aliasName) {
        String eltId = ctx.getContingencyListId(aliasName);
        ExploreRequests.getInstance().removeElement(eltId, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @And("remove filter {string}")
    public void removeFilter(String aliasName) {
        String eltId = ctx.getFilterId(aliasName);
        ExploreRequests.getInstance().removeElement(eltId, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @And("remove study {string}")
    public void removeStudy(String aliasName) {
        String eltId = ctx.getStudyId(aliasName);
        ExploreRequests.getInstance().removeElement(eltId, EnvProperties.getInstance().getUserName());
    }

    // --------------------------------------------------------
    @Then("{string} cardinality is {int}")
    public void cardinalityIs(String dataName, int cardNumber) {
        JsonNode jsonRoot = ctx.getJsonData(dataName);
        assertEquals("data size error", cardNumber, jsonRoot.size());
    }

    // --------------------------------------------------------
    @But("{string} does not contain element {string}")
    public void doesNotContainElement(String dataName, String eltName) {
        hasElement(dataName, eltName, null, false);
    }

    // --------------------------------------------------------
    @But("{string} contains element {string}")
    public void containsElement(String dataName, String eltName) {
        hasElement(dataName, eltName, null, true);
    }

    // --------------------------------------------------------
    @But("{string} contains directory {string}")
    public void containsDirectory(String dataName, String eltName) {
        hasElement(dataName, eltName, "DIRECTORY", true);
    }

    // --------------------------------------------------------
    @But("{string} does not contain directory {string}")
    public void doesNotContainDirectory(String dataName, String eltName) {
        hasElement(dataName, eltName, "DIRECTORY", false);
    }

    // --------------------------------------------------------
    @But("{string} contains contingency-list {string}")
    public void containsContingencyList(String dataName, String eltName) {
        hasElement(dataName, eltName, "CONTINGENCY_LIST", true);
    }

    // --------------------------------------------------------
    @But("{string} does not contain contingency-list {string}")
    public void doesNotContainsContingencyList(String dataName, String eltName) {
        hasElement(dataName, eltName, "CONTINGENCY_LIST", false);
    }

    // --------------------------------------------------------
    @But("{string} contains filter {string}")
    public void containsFilter(String dataName, String eltName) {
        hasElement(dataName, eltName, "FILTER", true);
    }

    // --------------------------------------------------------
    @But("{string} does not contain filter {string}")
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

            if (eltName.equalsIgnoreCase(oneElement.get(elementName).asText())) {
                if (eltType == null) {
                    found = true;
                } else if (eltType.equalsIgnoreCase(oneElement.get(elementType).asText())) {
                    found = true;
                }
            }
        }
        if (shouldExist) {
            assertTrue("element should exist: " + eltName, found);
        } else {
            assertFalse("element should not exist: " + eltName, found);
        }
    }

    // --------------------------------------------------------
    @Then("{string} directory is public")
    public void directoryIsPublic(String dirName) {
        ctx.directoryAccess(dirName, false);
    }

    // --------------------------------------------------------
    @Then("{string} directory is private")
    public void directoryIsPrivate(String dirName) {
        ctx.directoryAccess(dirName, true);
    }

    // --------------------------------------------------------
    @Then("directory {string} is public and owned by {string}")
    public void directoryIsPublicAndOwnedBy(String dirName, String user) {
        ctx.directoryAccess(dirName, false);
        ctx.elementOwner(dirName, user);
    }

    // --------------------------------------------------------
    @Then("directory {string} is private and owned by {string}")
    public void directoryIsPrivateAndOwnedBy(String dirName, String user) {
        ctx.directoryAccess(dirName, true);
        ctx.elementOwner(dirName, user);
    }

    // --------------------------------------------------------
    @When("move filter {string} into {string}")
    public void moveFilterInto(String eltName, String targetDirName) {
        ctx.moveElement(ctx.getFilterId(eltName), targetDirName, 200);
    }

    // --------------------------------------------------------
    @When("move contingency-list {string} into {string}")
    public void moveContingencyListInto(String eltName, String targetDirName) {
        ctx.moveElement(ctx.getContingencyListId(eltName), targetDirName, 200);
    }

    // --------------------------------------------------------
    @When("move contingency-list {string} into {string} catch {int}")
    public void moveContingencyListIntoCatch(String eltName, String targetDirName, int expectedHttpCode) {
        ctx.moveElement(ctx.getContingencyListId(eltName), targetDirName, expectedHttpCode);
    }
}
