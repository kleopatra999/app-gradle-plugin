/*
 * Copyright (c) 2016 Google Inc. All Right Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.google.cloud.tools.gradle.appengine;

import com.google.cloud.tools.appengine.cloudsdk.CloudSdk;
import com.google.cloud.tools.appengine.cloudsdk.internal.process.ProcessRunnerException;
import com.google.cloud.tools.appengine.cloudsdk.process.NonZeroExceptionExitListener;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** End to end tests for standard projects. */
@RunWith(Parameterized.class)
public class AppEngineStandardPluginIntegrationTest {

  /** Parameterize the project source for the test. */
  @Parameterized.Parameters
  public static Object[] data() {
    return new Object[] {
      "src/integTest/resources/projects/standard-project",
      "src/integTest/resources/projects/standard-project-java8"
    };
  }

  @Rule public Timeout globalTimeout = Timeout.seconds(180);

  @Rule public TemporaryFolder testProjectDir = new TemporaryFolder();

  private final String testProjectSrcDirectory;

  public AppEngineStandardPluginIntegrationTest(String testProjectSrcDirectory) {
    this.testProjectSrcDirectory = testProjectSrcDirectory;
  }

  @Before
  public void setUp() throws IOException {
    FileUtils.copyDirectory(new File(testProjectSrcDirectory), testProjectDir.getRoot());
  }

  @Ignore
  @Test
  public void testDevAppServer_sync() {
    // TODO : write test for devapp server running in synchronous mode
  }

  /**
   * If this test is failing, make sure you've set JAVA_HOME=some-jdk7, it might have something to
   * do with the way dev_appserver.py is launching java.
   */
  @Test
  public void testDevAppServer_async() throws InterruptedException, IOException {
    try {
      GradleRunner.create()
          .withProjectDir(testProjectDir.getRoot())
          .withPluginClasspath()
          .withArguments("appengineStart")
          .build();

      File expectedLogFileDir = new File(testProjectDir.getRoot(), "/build/tmp/appengineStart");
      DirectoryScanner ds = new DirectoryScanner();
      ds.setIncludes(new String[] {"dev_appserver*.out"});
      ds.setBasedir(expectedLogFileDir);
      ds.scan();
      String[] devAppserverLogFiles = ds.getIncludedFiles();
      Assert.assertEquals(1, devAppserverLogFiles.length);
      String devAppServerOutput =
          FileUtils.readFileToString(new File(expectedLogFileDir, devAppserverLogFiles[0]));
      System.out.println(devAppServerOutput);
      Assert.assertTrue(devAppServerOutput.contains("Dev App Server is now running")
              || devAppServerOutput.contains("INFO:oejs.Server:main: Started"));

      AssertConnection.assertResponse(
          "http://localhost:8080", 200, "Hello from the App Engine Standard project.");

      GradleRunner.create()
          .withProjectDir(testProjectDir.getRoot())
          .withPluginClasspath()
          .withArguments("appengineStop")
          .build();

      // give the server a couple seconds to come down
      Thread.sleep(8000);

      AssertConnection.assertUnreachable("http://localhost:8080");
    } finally {
      // just in case the test fails, make sure we stop the dev appserver
      GradleRunner.create()
          .withProjectDir(testProjectDir.getRoot())
          .withPluginClasspath()
          .withArguments("appengineStop")
          .build();
    }
  }

  @Test
  public void testDeploy() throws ProcessRunnerException, IOException {
    BuildResult buildResult =
        GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withPluginClasspath()
            .withDebug(true)
            .withArguments("appengineDeploy")
            .build();

    Assert.assertThat(
        buildResult.getOutput(),
        CoreMatchers.containsString("Deployed service [standard-project]"));

    CloudSdk cloudSdk =
        new CloudSdk.Builder().exitListener(new NonZeroExceptionExitListener()).build();
    cloudSdk.runAppCommand(Arrays.asList("services", "delete", "standard-project"));
  }
}
