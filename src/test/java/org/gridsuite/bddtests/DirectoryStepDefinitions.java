/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests;

import io.cucumber.java.en.Given;
import org.gridsuite.bddtests.common.EnvProperties;
import org.gridsuite.bddtests.common.TestContext;

public class DirectoryStepDefinitions {

    private final TestContext ctx;

    // DI with PicoContainer to share the same context among all steps classes
    public DirectoryStepDefinitions(TestContext ctx) {
        this.ctx = ctx;
    }

    // --------------------------------------------------------
    @Given("using tmp directory as {string}")
    public void usingTmpDirectoryAs(String aliasName) {
        ctx.createTmpDirectoryAs(aliasName, EnvProperties.getInstance().getUserName(), false);
    }
}
