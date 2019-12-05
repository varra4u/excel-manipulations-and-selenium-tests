package com.varra.excel;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

import com.varra.log.Logger;
import lombok.Data;
import org.junit.*;

import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.Select;

import static com.varra.util.StringUtils.isNotBlank;
import static org.openqa.selenium.By.xpath;

@Data
public class ReviewFileUploader {
  private WebDriver driver;
  private final String baseUrl = "http://rdfgdgdfg";
  private final String pdfOutputDir = "fgdfgdfgdfgfdgdfg";
  private final String xmlFilePath = "dfgdfggfdfg";
  private final String USERNAME = "dfgdfg";
  private final String PASSWORD = "dfgdfgdfg";
  private final Logger log = Logger.getLogger(ReviewFileUploader.class);
  private boolean headless = false;
  private int totalNumberOfRetries = 10;
  private final AtomicInteger numberOfRetries = new AtomicInteger();

  @Before
  public void setUp() throws Exception {
    System.setProperty("webdriver.gecko.driver","./lib/geckodriver.exe");
    driver = new FirefoxDriver(setUpAndGetFirefoxOptions());
    driver.manage().timeouts().implicitlyWait(1, TimeUnit.SECONDS);
  }

  private FirefoxOptions setUpAndGetFirefoxOptions() {
    // Creating FirefoxOptions
    final FirefoxOptions options = new FirefoxOptions();
    if (headless)
    {
      // for headless binary
      final FirefoxBinary firefoxBinary = new FirefoxBinary();
      firefoxBinary.addCommandLineOptions("--headless");
      options.setBinary(firefoxBinary);
    }
    // Creating firefox profile
    final FirefoxProfile profile = new FirefoxProfile();

    // Instructing firefox to use custom download location
    profile.setPreference("browser.download.folderList", 2);

    // Setting custom download directory
    profile.setPreference("browser.download.dir", pdfOutputDir);

    // Skipping Save As dialog box for types of files with their MIME
    profile.setPreference("browser.helperApps.neverAsk.saveToDisk",
            "text/csv, application/pdf, application/java-archive, application/x-msexcel,application/excel,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/x-excel,application/vnd.ms-excel,image/png,image/jpeg,text/html,text/plain,application/msword,application/xml,application/vnd.microsoft.portable-executable");

    profile.setPreference("pdfjs.disabled", true);

    // Use this to disable Acrobat plugin for previewing PDFs in Firefox (if you have Adobe reader installed on your computer)
    profile.setPreference("plugin.scan.Acrobat", "99.0");
    profile.setPreference("plugin.scan.plid.all", false);

    //set profile
    options.setProfile(profile);
    return options;
  }

  public void processXMLFilesAndGetPdfs() throws Exception
  {
    setUp();
    processXMLFilesAndGetPdfsTest();
  }

  @Test
  public void processXMLFilesAndGetPdfsTest() throws Exception {
    final int noOfFilesToBeUploaded = getNumberOfFilesToBeUploaded();
    log.info("Going to process the job with "+noOfFilesToBeUploaded+" files inside the provided job.");
    driver.get(baseUrl);
    driver.findElement(By.name("userShortName")).sendKeys(USERNAME);
    driver.findElement(By.name("userPassword")).sendKeys(PASSWORD);
    driver.findElement(By.name("login_panel_04")).click();
    driver.findElement(By.linkText("job status")).click();
    Select select = new Select(driver.findElement(xpath("//*[@id=\"header\"]/tbody/tr/td[3]/table/tbody/tr/td/select")));
    select.selectByValue("629");
    final StringBuilder parentWindow = new StringBuilder(driver.getWindowHandle());
    String childWindow = null;
    driver.findElement(By.linkText("submit job")).click();
    if (parentWindow.toString().equalsIgnoreCase(driver.getWindowHandle()))
    {
        childWindow = driver.getWindowHandles().stream().filter(h -> !parentWindow.toString().equalsIgnoreCase(h)).findAny().orElse(parentWindow.toString());
        driver.switchTo().window(childWindow);
    }
    uploadFile(driver);
    driver.switchTo().window(parentWindow.toString());

    goToLatestJobWindow(driver);

    parentWindow.setLength(0);
    parentWindow.append(driver.getWindowHandle());
    downloadFiles(driver, parentWindow.toString(), noOfFilesToBeUploaded);

    driver.findElement(By.linkText("logout")).click();
    driver.close();
  }

  private int getNumberOfFilesToBeUploaded() throws IOException {
    int numberOfFilesToBeUploaded = 0;
    try (ZipInputStream zipIs = new ZipInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(xmlFilePath))))) {
      while ((zipIs.getNextEntry()) != null) {
        numberOfFilesToBeUploaded++;
      }
    }
    return numberOfFilesToBeUploaded;
  }

  private void downloadFiles(WebDriver driver, String parentWindow, int noOfFilesToBeDownloaded) throws InterruptedException {
    /*while (toBeDownloadedArray.size() != noOfFilesToBeDownloaded)
    {
      log.info("Waiting for the pdf files to be displayed... so far found: "+toBeDownloadedArray.size()+", expected: "+noOfFilesToBeDownloaded);
      TimeUnit.SECONDS.sleep(5);
      driver.navigate().refresh();
      toBeDownloadedArray = driver.findElements(xpath("//table[@id='tableContent']//a"));
    }*/
    final List<WebElement> toBeDownloadedArray = driver.findElements(xpath("//table[@id='tableContent']//a"));
    final Supplier<Boolean> visibleFilesNotEqualToTotalNumber = () -> {
      toBeDownloadedArray.clear();
      toBeDownloadedArray.addAll(driver.findElements(xpath("//table[@id='tableContent']//a")));
      log.info("Waiting for the pdf files to be displayed... so far found: "+toBeDownloadedArray.size()+", expected: "+noOfFilesToBeDownloaded);
      return toBeDownloadedArray.size() != noOfFilesToBeDownloaded;
    };
    makeItWaitWhileConditionIsTrue(10, "", visibleFilesNotEqualToTotalNumber, () -> driver.navigate().refresh());
    //toBeDownloadedArray = driver.findElements(xpath("//table[@id='tableContent']//a"));
    log.info("Able to see all the pdf files, going to download them.");
    for(WebElement element : toBeDownloadedArray){
      element.click();
      downloadFile(element.getText()+".pdf", driver, parentWindow);
      driver.switchTo().window(parentWindow);
    }
    log.info("All files have been downloaded successfully, total number of files: "+noOfFilesToBeDownloaded);
  }

  private void downloadFile(String filename, WebDriver driver, String parentWindow) throws InterruptedException {
    /*while (driver.getWindowHandles().size() == 1)
    {
      log.info("Waiting for the file to be downloaded.. filename: "+filename);
      TimeUnit.SECONDS.sleep(1);
    }*/
    makeItWaitWhileConditionIsTrue(1, "Preparing the file to be downloaded.. filename: "+filename, () -> driver.getWindowHandles().size() == 1);
    if (parentWindow.equalsIgnoreCase(driver.getWindowHandle()))
    {
      driver.switchTo().window(driver.getWindowHandles().stream().filter(h -> !parentWindow.equalsIgnoreCase(h)).findAny().orElse(parentWindow));
    }
    driver.findElement(xpath("//input[@name='outputFilter']")).click();
    driver.findElement(By.id("submitButton")).click();
/*    while (driver.getWindowHandles().size() > 1)
    {
      log.info("Waiting for the file to be downloaded.. filename: "+filename);
      TimeUnit.SECONDS.sleep(5);
      driver.close();
    }*/
    makeItWaitWhileConditionIsTrue(2, "Waiting for the file to be downloaded.. filename: "+filename, () -> driver.getWindowHandles().size() > 1, driver::close);
    log.info("File has been downloaded successfully, filename: "+filename);
  }

  private void uploadFile(WebDriver driver) throws InterruptedException {
    driver.findElement(By.name("xmlData")).sendKeys(xmlFilePath);
    driver.findElement(xpath("//input[@value='Submit']")).click();
/*    while (driver.getWindowHandles().size() > 1)
    {
      log.info("Waiting for the file upload to be completed..");
      TimeUnit.SECONDS.sleep(5);
    }*/
    makeItWaitWhileConditionIsTrue(3, "Waiting for the file upload to be completed..", () -> driver.getWindowHandles().size() > 1);
    log.info("File upload has been completed, successfully.");
  }

  private void goToLatestJobWindow(WebDriver driver) throws InterruptedException {
/*    while (uploadArray.isEmpty())
    {
      log.info("Waiting for the latest job run to be displayed..");
      TimeUnit.SECONDS.sleep(5);
      driver.navigate().refresh();
      uploadArray = driver.findElements(xpath("//table[@id='tableContent']//a"));
    }*/
    makeItWaitWhileConditionIsTrue(5, "Waiting for the latest job run to be displayed..", driver.findElements(xpath("//table[@id='tableContent']//a"))::isEmpty, () -> driver.navigate().refresh());
    final List<WebElement> uploadArray = driver.findElements(xpath("//table[@id='tableContent']//a"));
    log.info("Latest job run has been found, and going to it.");
    uploadArray.get(uploadArray.size()-1).click();
  }

  private void makeItWaitWhileConditionIsTrue(int seconds, String logMessage, Supplier<Boolean> condition, Runnable... statusUpdaters) throws InterruptedException {
    numberOfRetries.set(0);
    while (condition.get())
    {
      if (numberOfRetries.incrementAndGet() > totalNumberOfRetries)
      {
          throw new RuntimeException("Took long time to process, so killing the process!");
      }
      if (isNotBlank(logMessage))
      {
        log.info(logMessage);
      }
      TimeUnit.SECONDS.sleep(seconds);
      Stream.of(statusUpdaters).forEach(Runnable::run);
    }
  }

  public static void main(String[] args) throws Exception {
    final ReviewFileUploader reviewFileUploader = new ReviewFileUploader();
    reviewFileUploader.setHeadless(true);
    reviewFileUploader.processXMLFilesAndGetPdfs();
  }
}
