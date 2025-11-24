# gridsuite-integration-test

This project is a POC to write some integration tests for Grid Suite, for both web applications and micro-services, using cucumber.

The goal is to validate if cucumber is relevant for non-developer people to write some integration tests with a pseudo natural language, called gherkin.

## Cucumber Plugin Installation

1) IntelliJ IDEA

Add the "plugin Cucumber for Java".

2) Eclipse

TODO

3) VSCode

TODO

## Selenium Web driver Installation
A web driver can be in the PATH (easiest way) or pointed by a property. We could also use the package io.github.bonigarcia.wdm.WebDriverManager, that carries out automatically the drivers management (to be tested).

1) Firefox

[Download](https://github.com/mozilla/geckodriver/releases) geckodriver and put it in the PATH.

2) Chrome

- [Download](https://chromedriver.chromium.org/downloadsi) chromedriver and put it in the PATH.
- Download and install Google Chrome (must be installed in standard location: /usr/bin/google-chrome).
- Both chrome and its driver must be compatible (same release).


3) others


TODO

## Usage

The cucumber tests, written in Gherkin syntax, are currently in a single feature file:
- Supervision.feature to define monitoring test cases.

We can run some cucumber tests (scenarios), using:
- the IDE, once the plugin is installed
- with 'mvn test'. Examples with all tests from a feature file, a single test (giving line number), two tests (2 line numbers), only one tag, all but 2 tags, two tags, and everything (all feature files):
```
mvn test -Dcucumber.features=classpath:org/gridsuite/bddtests/StudySrv.feature
mvn test -Dcucumber.features=classpath:org/gridsuite/bddtests/StudySrv.feature:35
mvn test -Dcucumber.features=classpath:org/gridsuite/bddtests/StudySrv.feature:35:40
mvn test -Dcucumber.filter.tags=@tagSupervision
mvn test -Dcucumber.filter.tags="not @tagInitData and not @tagWebApp"
mvn test -Dcucumber.filter.tags="@tag1 and @tag2"
mvn test
```
Features can be run on different platforms:
- local: all REST calls are sent to localhost (no SSO token required), or using the gateway (SSO token required)
- demo : Azure public platform using https://demo.gridsuite.org/gridexplore


The platform is defined in the Gerkhin feature file:
```
  Background:
    Given using platform "local"
```
Or overwritten as a property:
```
mvn test -Dcucumber.filter.tags=@tagExample -Dusing_platform=demo
mvn test -Dcucumber.features=classpath:org/gridsuite/bddtests/StudySrv.feature:133 -Dusing_platform=local
```

When using an authentificated mode through the gateway, we must provide the 'bearer' token.
We can use a nerver-expiring token, otherwise we have to retrieve it once the user has logged in explore webapp.
In both cases, we have to set it in the properties file, like in demo_env.properties:
```
api_hostname=https://demo.gridsuite.org/gridexplore/api/gateway
bearer=eyJhbGciO...R0-CyUsC34keTQ
```

Report and Re-run : by default each test execution generates two files:
- bddtests_report.html : a cucumber HTML report
- bddtests_failure_to_rerun.txt : a file containing all tests in failure

To re-run only this failed tests, we can run:
```
mvn test -Dcucumber.features=@bddtests_failure_to_rerun.txt
```

Random: a test must not depend from a given order. To randomize:
```
mvn test -Dcucumber.filter.tags=@tagExample -Dusing_platform=demo -Dcucumber.execution.order=random
```

Dry-run: no effect run
```
mvn test -Dcucumber.filter.tags=@tagExample -Dcucumber.execution.dry-run=true
```
