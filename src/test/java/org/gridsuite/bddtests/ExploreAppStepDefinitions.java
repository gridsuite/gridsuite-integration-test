/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.bddtests;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.gridsuite.bddtests.common.EnvProperties;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.*;

public class ExploreAppStepDefinitions {


    enum WebApp {
        EXPLORE,
        STUDY
    }

    private String windowGridExplore = null;
    private RemoteWebDriver driver = null;
    private final boolean loggued = false;
    private String browserName = null;
    private WebApp currentApp = WebApp.EXPLORE;
    private int pause = 0;
    private final String logginButtonXpath = "//div[@id='root']//button[contains(.,'Connexion')]";

    private static final Logger LOGGER = LoggerFactory.getLogger(StudySrvStepDefinitions.class);

    // --------------------------------------------------------
    private WebElement checkElementToBeClickable(String xpath) {
        WebElement element;
        try {
            element = getElementToBeClickable(xpath);
        } catch (Exception exception) {
            element = null;
        }
        return element;
    }

    // --------------------------------------------------------
    private WebElement getElementToBeClickable(String xpath) {
        WebDriverWait condition = new WebDriverWait(driver, Duration.ofSeconds(10));
        return condition.pollingEvery(Duration.ofSeconds(1)).until(
            ExpectedConditions.elementToBeClickable(By.xpath(xpath))
        );
    }

    // --------------------------------------------------------
    private WebElement getElementLocated(String xpath) {
        return getElementLocatedTimeout(xpath, 10);
    }

    private WebElement getElementLocatedTimeout(String xpath, int timeout) {
        WebDriverWait condition = new WebDriverWait(driver, Duration.ofSeconds(timeout));
        return condition.pollingEvery(Duration.ofSeconds(1)).until(
                ExpectedConditions.presenceOfElementLocated(By.xpath(xpath))
        );
    }

    // --------------------------------------------------------
    private WebElement checkElementLocated(String xpath) {
        WebElement element;
        try {
            element = getElementLocated(xpath);
        } catch (Exception exception) {
            element = null;
        }
        return element;
    }

    // --------------------------------------------------------
    private void pause()  {
        pause(pause);
    }

    // --------------------------------------------------------
    private void pause(int seconds)  {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // --------------------------------------------------------
    private String userParamButtonName(WebApp appType)  {
        if (appType == WebApp.EXPLORE) {
            return "//div[@id='root']/div/header/div/div[4]/button/span[1]";
        } else {
            return "/html/body/div[1]/div/header/div/div[5]/button/span[1]";
        }
    }

    // --------------------------------------------------------
    @And("pause {int} seconds")
    public void pauseSeconds(int nbSeconds) {
        try {
            Thread.sleep(nbSeconds * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // --------------------------------------------------------
    private void doLogin(WebElement loginButton) {
        // try to login, with CONNEXION button
        loginButton.click();

        // 2 cases: login/passwd asked, or just confirmation/authorization button
        WebElement loginField = checkElementToBeClickable("//input[@name='login']");
        if (loginField != null) {
            // next page content : 3 elements
            WebElement passwordField = getElementToBeClickable("//input[@name='password']");
            WebElement signInButton = getElementToBeClickable("//button[@type='submit']");  // or //button[contains(.,'Sign-in')]
            loginField.sendKeys(EnvProperties.getInstance().getUserName());
            passwordField.sendKeys(EnvProperties.getInstance().getUserName());
            signInButton.click();
        }

        // next page content : 1 element (new Authorization button) => same xpath than signInButton !
        WebElement newAuthButton = getElementToBeClickable("//button[@type='submit']");  // or //button[contains(.,'Continue')]
        newAuthButton.click();
    }

    // --------------------------------------------------------
    // setup before each scenario (called before BACKGROUND)
    @Before
    public void setup() {
        LOGGER.info("ExploreAppStepDefinitions setup");
    }

    // --------------------------------------------------------
    // teardown each scenario
    @After
    public void teardown() {
        LOGGER.info("ExploreAppStepDefinitions teardown");
        if (driver != null) {
            pause();
            driver.quit();
        }
    }

    // --------------------------------------------------------
    // BACKGROUND conditions
    @Given("using browser {string}")
    public void usingBrowser(String browser) {
        browserName = browser;
    }

    // --------------------------------------------------------
    @Given("using browsers")
    public void usingBrowsers(DataTable browserList) {
        List<List<String>> data = browserList.asLists(String.class);
        List<String> browserNames = data.get(0); // TODO not used for now
    }

    // --------------------------------------------------------
    @And("using pause {int}")
    public void usingPause(int pauseDurationInSec) {
        pause = pauseDurationInSec;
    }

    // --------------------------------------------------------
    @When("start browser")
    public void startBrowser() {
        // start browser
        // TODO can we use a common/shared Env Variable or location to easily locate geckodriver ?
        // TODO or put the driver in the PATH somewhere => easier ?
        // TODO or using import io.github.bonigarcia.wdm.WebDriverManager;
        //System.setProperty("webdriver.chrome.driver", "/home/braquartdav/Projects/GridSuite/WORK/Selenium/web_drivers/chromedriver");

        // TODO Firefox issue with Proxy on RTE network !!  (ok with Chrome)
        if (browserName.equalsIgnoreCase("firefox")) {
            driver = new FirefoxDriver();
        } else if (browserName.equalsIgnoreCase("chrome")) {
            driver = new ChromeDriver();
        }
        LOGGER.info("Openning URL '{}'", EnvProperties.getInstance().getExploreUrl());
        driver.get(EnvProperties.getInstance().getExploreUrl());
        driver.manage().window().maximize();
    }

    // --------------------------------------------------------
    @Then("webapp name is {string}")
    public void webappNameIs(String titleName) {
        WebDriverWait condition = new WebDriverWait(driver, Duration.ofSeconds(10));
        condition.pollingEvery(Duration.ofSeconds(1)).until(ExpectedConditions.titleIs(titleName));
        assertEquals("Tittle not found in current tab/webpage", titleName, driver.getTitle());
        if (titleName.equalsIgnoreCase("GridExplore")) { // TODO how to manage N tabs ?
            windowGridExplore = driver.getWindowHandle();
        }
        pause();
    }


    // --------------------------------------------------------
    @Then("login required")
    public void loginRequired() {
        // we must find the "Connexion" button
        getElementLocated(logginButtonXpath);
        pause();
    }

    // --------------------------------------------------------
    @When("autologin or login")
    public void autologinOrLogin() {
        // 2 cases : we are already log in, or we have to

        // do we have to log in ? (CONNEXION button is here)
        // /html/body/div/div/div/main/div[1]/button
        // html.singlestretch-parent body.singlestretch-parent.singlestretch-child div#root.singlestretch-parent.singlestretch-child div.singlestretch-child div main.MuiContainer-root.MuiContainer-maxWidthXs.css-hltdia div.jss22 button.MuiButton-root.MuiButton-contained.MuiButton-containedPrimary.MuiButton-sizeMedium.MuiButton-containedSizeMedium.MuiButton-fullWidth.MuiButtonBase-root.jss24.css-tstppz
        // "//div[@id='root']//button/span"

        WebElement loginButton = checkElementToBeClickable(logginButtonXpath);
        // can we see the user/param button ? (header, on the right, with the user first LETTER)
        WebElement userParamButton = null;
        if (loginButton == null) {
            userParamButton = checkElementToBeClickable(userParamButtonName(currentApp));
        }

        // at least one condition expected
        assertTrue("Must be logged or asked to log in", userParamButton != null || loginButton != null);

        if (userParamButton != null) {
            return;  // already logged
        }

        doLogin(loginButton);
    }

    // --------------------------------------------------------
    @Then("user logged")
    public void userLogged() {
        WebElement userParamButton = getElementLocated(userParamButtonName(currentApp));
        // capitalize first letters of our user name: "john paul" -> "JP"
        StringBuilder firstUpperLetters = new StringBuilder();
        String[] words = EnvProperties.getInstance().getUserName().trim().split("\\s");
        for (String w : words) {
            firstUpperLetters.append(w.toUpperCase().charAt(0));
        }
        // "JP" should be the label of the "param" button
        assertEquals("Not logged: No param button with expected letter(s)", firstUpperLetters.toString(), userParamButton.getText());
        pause();
    }

    // --------------------------------------------------------
    @When("logout")
    public void logout() {

        WebElement userParamButton = getElementLocated(userParamButtonName(currentApp));
        userParamButton.click();

        WebElement logoutButton = getElementToBeClickable("//p[contains(.,'Se déconnecter')]");
        logoutButton.click();

        // next page content : 1 element (Confirmation button)
        WebElement confirmButton = getElementToBeClickable("//button[@name='logout']");
        confirmButton.click();
    }

    // --------------------------------------------------------
    @When("login")
    public void login() {
        WebElement loginButton = getElementToBeClickable(logginButtonXpath);
        doLogin(loginButton);

        // TODO see how cookies can be used - currently size is 0
        LOGGER.info("Post login cookie Size {}", driver.manage().getCookies().size());
        for (Cookie ck : driver.manage().getCookies()) {
            LOGGER.info("Post login cookie {}", ck.toString());
            LOGGER.info("Post login cookie {} {} {} {} {} {}", ck.getName(), ck.getValue(), ck.getDomain(), ck.getPath(), ck.getExpiry(), ck.isSecure());
        }
    }

    // --------------------------------------------------------
    @When("select directory {string}")
    public void selectDirectory(String directoryName) {
        WebElement directoryButton = getElementToBeClickable("//p[contains(.,'" + directoryName + "')]");
        directoryButton.click();
    }

    // --------------------------------------------------------
    @And("open study {string}")
    public void openStudy(String studyName) {
        // TODO with the type too ?
        // note: the [*] in the xpath below is the line number of the element list
        WebElement studyLine = getElementToBeClickable("/html/body/div[1]/div/div/div/div[2]/div/div[2]/div[1]/div[2]/div/div[1]/div/div[2]/div/div[*]/div[2]/div/div[text() = '" + studyName + "']");
        studyLine.click();
    }

    // --------------------------------------------------------
    @Then("new webapp tab")
    public void newWebappTab() {
        // We suppose we have a single tab "GridExplore", and we have opened a second tab "GridStudy"
        final int nbTabs = 2;
        WebDriverWait condition = new WebDriverWait(driver, Duration.ofSeconds(10));
        Boolean twoTab = condition.pollingEvery(Duration.ofSeconds(1))
            .until(ExpectedConditions.numberOfWindowsToBe(nbTabs));

        String windowGridStudy = null;
        for (String windowHandle : driver.getWindowHandles()) {
            if (!windowGridExplore.contentEquals(windowHandle)) {
                // switching is mandatory for the driver to "go" on the new tab
                driver.switchTo().window(windowHandle);
                windowGridStudy = driver.getWindowHandle();
                break;
            }
        }
        assertNotNull("Cannot find new window", windowGridStudy);
        currentApp = WebApp.STUDY;
    }

    // --------------------------------------------------------
    @And("find element {string} timeout {int}")
    public void findElement(String elementName, int timeout) {
        getElementLocatedTimeout("//*[contains(.,'" + elementName + "')]", timeout);
    }
}
