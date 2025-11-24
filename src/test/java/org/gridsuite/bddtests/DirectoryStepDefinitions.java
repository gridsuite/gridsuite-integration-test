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
    @Given("using tmp directory as {string}")
    public void usingTmpDirectoryAs(String aliasName) {
        ctx.createTmpDirectoryAs(aliasName, EnvProperties.getInstance().getUserName(), false);
    }
}
