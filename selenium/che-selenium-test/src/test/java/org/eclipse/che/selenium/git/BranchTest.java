/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.selenium.git;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.net.URL;
import java.nio.file.Paths;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.selenium.core.TestGroup;
import org.eclipse.che.selenium.core.client.TestProjectServiceClient;
import org.eclipse.che.selenium.core.client.TestUserPreferencesServiceClient;
import org.eclipse.che.selenium.core.constant.TestGitConstants;
import org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants;
import org.eclipse.che.selenium.core.project.ProjectTemplates;
import org.eclipse.che.selenium.core.user.DefaultTestUser;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.pageobject.AskDialog;
import org.eclipse.che.selenium.pageobject.AskForValueDialog;
import org.eclipse.che.selenium.pageobject.CodenvyEditor;
import org.eclipse.che.selenium.pageobject.Events;
import org.eclipse.che.selenium.pageobject.Ide;
import org.eclipse.che.selenium.pageobject.Loader;
import org.eclipse.che.selenium.pageobject.Menu;
import org.eclipse.che.selenium.pageobject.ProjectExplorer;
import org.openqa.selenium.Keys;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** @author Aleksandr Shmaraev */
@Test(groups = TestGroup.GITHUB)
public class BranchTest {
  private static final String PROJECT_NAME = NameGenerator.generate("Branch_", 4);
  private static final String APP_JAVA_TAB_NAME = "AppController";
  private static final String HELLO_JAVA_TAB_NAME = "Hello";
  private static final String JSP_TAB_NAME = "index.jsp";
  private static final String APP_JAVA_PATH =
      "/src/main/java/org/eclipse/qa/examples/AppController.java";
  private static final String HELLO_JAVA_PATH = "/src/main/java/org/eclipse/qa/examples/Hello.java";
  private static final String JSP_FILE_PATH = "/src/main/webapp/index.jsp";
  private static final String SCRIPT_FILE_PATH = "/src/main/webapp/script.js";
  private final String MASTER_BRANCH = "master";
  private final String TEST_BRANCH = "newbranch";
  private static final String CHANGE_CONTENT_1 = "<!-change->";
  private static final String CHANGE_CONTENT_2 = "<!--**change**-->";

  private static final String STATUS_MESSAGE_BEFORE_ADD =
      " On branch newbranch\n"
          + " Changes not staged for commit:\n"
          + "  new file:   src/main/webapp/script.js\n"
          + " new file:   src/main/java/org/eclipse/qa/examples/Hello.java\n"
          + " modified:   src/main/webapp/index.jsp\n"
          + " modified:   src/main/java/org/eclipse/qa/examples/AppController.java";

  private static final String STATUS_MESSAGE_AFTER_ADD =
      " On branch newbranch\n"
          + " Changes to be committed:\n"
          + "  new file:   src/main/webapp/script.js\n"
          + " new file:   src/main/java/org/eclipse/qa/examples/Hello.java\n"
          + " modified:   src/main/webapp/index.jsp\n"
          + " modified:   src/main/java/org/eclipse/qa/examples/AppController.java";

  private static final String STATUS_MESSAGE_AFTER_COMMIT =
      " On branch newbranch\n" + " nothing to commit, working directory clean";

  private static final String STATUS_MASTER_BRANCH =
      " On branch master\n" + " nothing to commit, working directory clean";

  private static final String CONFLICT_MESSAGE =
      " Checkout conflict with files: \n"
          + "src/main/java/org/eclipse/qa/examples/AppController.java\n"
          + "src/main/webapp/index.jsp";

  @Inject private TestWorkspace ws;
  @Inject private Ide ide;
  @Inject private DefaultTestUser user;

  @Inject
  @Named("github.username")
  private String gitHubUsername;

  @Inject private ProjectExplorer projectExplorer;
  @Inject private Menu menu;
  @Inject private AskDialog askDialog;
  @Inject private org.eclipse.che.selenium.pageobject.git.Git git;
  @Inject private Events events;
  @Inject private Loader loader;
  @Inject private CodenvyEditor editor;
  @Inject private AskForValueDialog askForValueDialog;
  @Inject private TestUserPreferencesServiceClient testUserPreferencesServiceClient;
  @Inject private TestProjectServiceClient testProjectServiceClient;

  @BeforeClass
  public void prepare() throws Exception {
    URL resource = getClass().getResource("/projects/checkoutSpringSimple");
    testUserPreferencesServiceClient.addGitCommitter(gitHubUsername, user.getEmail());
    testProjectServiceClient.importProject(
        ws.getId(), Paths.get(resource.toURI()), PROJECT_NAME, ProjectTemplates.MAVEN_SPRING);
    ide.open(ws);
  }

  @Test
  public void checkoutBranchTest() {
    // perform init commit
    projectExplorer.waitProjectExplorer();
    projectExplorer.waitItem(PROJECT_NAME);
    projectExplorer.waitAndSelectItem(PROJECT_NAME);
    menu.runCommand(
        TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.INITIALIZE_REPOSITORY);
    loader.waitOnClosed();
    askDialog.confirmAndWaitClosed();
    git.waitGitStatusBarWithMess(TestGitConstants.GIT_INITIALIZED_SUCCESS);
    events.clickEventLogBtn();
    events.waitExpectedMessage(TestGitConstants.GIT_INITIALIZED_SUCCESS);
    projectExplorer.waitAndSelectItem(PROJECT_NAME);
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.COMMIT);
    loader.waitOnClosed();
    git.waitAndRunCommit("init");
    projectExplorer.quickExpandWithJavaScript();
    loader.waitOnClosed();
    createBranch();
    switchOnTestBranch();

    // create change in AppController.java
    projectExplorer.openItemByPath(PROJECT_NAME + APP_JAVA_PATH);
    editor.setCursorToLine(15);
    editor.typeTextIntoEditor("//some change");
    editor.waitTextIntoEditor("//some change");
    loader.waitOnClosed();

    // Create change in index.jsp
    projectExplorer.openItemByPath(PROJECT_NAME + JSP_FILE_PATH);
    editor.waitActive();
    editor.typeTextIntoEditor(CHANGE_CONTENT_1);
    editor.waitTextIntoEditor(CHANGE_CONTENT_1);
    loader.waitOnClosed();

    // Create Hello.java class
    projectExplorer.waitAndSelectItem(PROJECT_NAME + "/src/main/java/org/eclipse/qa/examples");
    menu.runCommand(
        TestMenuCommandsConstants.Project.PROJECT,
        TestMenuCommandsConstants.Project.New.NEW,
        TestMenuCommandsConstants.Project.New.JAVA_CLASS);
    askForValueDialog.waitNewJavaClassOpen();
    askForValueDialog.typeTextInFieldName("Hello");
    askForValueDialog.clickOkBtnNewJavaClass();
    askForValueDialog.waitNewJavaClassClose();
    projectExplorer.openItemByPath(PROJECT_NAME + HELLO_JAVA_PATH);
    loader.waitOnClosed();

    // Create script.js file
    projectExplorer.waitAndSelectItem(PROJECT_NAME + "/src/main/webapp");
    menu.runCommand(
        TestMenuCommandsConstants.Project.PROJECT,
        TestMenuCommandsConstants.Project.New.NEW,
        TestMenuCommandsConstants.Project.New.JAVASCRIPT_FILE);
    askForValueDialog.waitFormToOpen();
    askForValueDialog.typeAndWaitText("script");
    askForValueDialog.clickOkBtn();
    askForValueDialog.waitFormToClose();

    // Check status
    projectExplorer.waitAndSelectItem(PROJECT_NAME + "/src/main");
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.STATUS);
    loader.waitOnClosed();
    git.waitGitStatusBarWithMess(STATUS_MESSAGE_BEFORE_ADD);

    // add all files to index and check status
    projectExplorer.waitAndSelectItem(PROJECT_NAME + "/src/main");
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.ADD_TO_INDEX);
    git.waitAddToIndexFormToOpen();
    git.confirmAddToIndexForm();
    git.waitGitStatusBarWithMess(TestGitConstants.GIT_ADD_TO_INDEX_SUCCESS);
    events.clickEventLogBtn();
    events.waitExpectedMessage(TestGitConstants.GIT_ADD_TO_INDEX_SUCCESS);
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.STATUS);
    git.waitGitStatusBarWithMess(STATUS_MESSAGE_AFTER_ADD);

    // commit to repository and check status
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.COMMIT);
    git.waitAndRunCommit("first commit");
    git.waitGitStatusBarWithMess(TestGitConstants.COMMIT_MESSAGE_SUCCESS);
    events.clickEventLogBtn();
    events.waitExpectedMessage(TestGitConstants.COMMIT_MESSAGE_SUCCESS);
    loader.waitOnClosed();
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.STATUS);
    git.waitGitStatusBarWithMess(STATUS_MESSAGE_AFTER_COMMIT);

    // checkout in main branch and check changed files
    switchOnMasterBranch();
    loader.waitOnClosed();
    editor.selectTabByName(APP_JAVA_TAB_NAME);
    editor.waitTextNotPresentIntoEditor("//some change");
    editor.selectTabByName(JSP_TAB_NAME);
    editor.waitTextNotPresentIntoEditor(CHANGE_CONTENT_1);
    projectExplorer.waitAndSelectItem(PROJECT_NAME + "/src/main");
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.STATUS);
    git.waitGitStatusBarWithMess(STATUS_MASTER_BRANCH);
    loader.waitOnClosed();
    projectExplorer.clickOnRefreshTreeButton();
    projectExplorer.waitDisappearItemByPath(PROJECT_NAME + HELLO_JAVA_PATH);
    projectExplorer.waitDisappearItemByPath(PROJECT_NAME + SCRIPT_FILE_PATH);

    // switch to test branch again and check earlier changes
    switchOnTestBranch();
    editor.selectTabByName(APP_JAVA_TAB_NAME);
    loader.waitOnClosed();
    editor.waitTextIntoEditor("//some change");
    editor.selectTabByName(JSP_TAB_NAME);
    editor.waitTextIntoEditor(CHANGE_CONTENT_1);
    projectExplorer.clickOnRefreshTreeButton();
    projectExplorer.quickRevealToItemWithJavaScript(PROJECT_NAME + HELLO_JAVA_PATH);
    loader.waitOnClosed();
    projectExplorer.openItemByPath(PROJECT_NAME + HELLO_JAVA_PATH);
    projectExplorer.openItemByPath(PROJECT_NAME + SCRIPT_FILE_PATH);
    loader.waitOnClosed();
    editor.closeFileByNameWithSaving("script.js");

    // Checkout in main branch, change files in master branch (this creates conflict) and check
    // message with conflict
    switchOnMasterBranch();
    projectExplorer.waitProjectExplorer();
    loader.waitOnClosed();

    // create change in GreetingController.java
    editor.selectTabByName(APP_JAVA_TAB_NAME);
    editor.setCursorToLine(21);
    editor.typeTextIntoEditor("//change in master branch");
    editor.waitTextIntoEditor("//change in master branch");
    editor.waitTabFileWithSavedStatus(APP_JAVA_TAB_NAME);
    loader.waitOnClosed();

    // create change in index.jsp
    editor.selectTabByName(JSP_TAB_NAME);
    editor.waitTextNotPresentIntoEditor(CHANGE_CONTENT_2);
    editor.typeTextIntoEditor(Keys.ENTER.toString());
    editor.typeTextIntoEditor(Keys.PAGE_UP.toString());
    editor.typeTextIntoEditor(CHANGE_CONTENT_2);
    editor.waitTextIntoEditor(CHANGE_CONTENT_2);
    editor.waitTabFileWithSavedStatus("index.jsp");
    loader.waitOnClosed();

    // Add all files to index and check status
    projectExplorer.waitAndSelectItem(PROJECT_NAME + "/src/main");
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.ADD_TO_INDEX);
    git.waitGitStatusBarWithMess(TestGitConstants.GIT_ADD_TO_INDEX_SUCCESS);
    events.clickEventLogBtn();
    events.waitExpectedMessage(TestGitConstants.GIT_ADD_TO_INDEX_SUCCESS);
    checkShwithConflict();
  }

  @Test(priority = 1)
  public void filterBranchesTest() {
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.BRANCHES);
    String defaultFilter = "Type from keyboard to filter branches";
    git.waitBranchSearchFilerWithText(defaultFilter);
    git.typeToBranchSearchFilter("newbranch");
    git.waitBranchSearchFilerWithText("newbranch");
    git.waitBranchInTheList("newbranch");
    git.waitDisappearBranchName("master");
    git.typeToBranchSearchFilter(Keys.ESCAPE.toString());
    git.waitBranchSearchFilerWithText(defaultFilter);
    git.waitBranchInTheList("master");
    git.waitBranchInTheList("newbranch");
  }

  private void createBranch() {
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.BRANCHES);
    git.waitBranchInTheList(MASTER_BRANCH);
    git.waitDisappearBranchName(TEST_BRANCH);
    git.waitEnabledAndClickCreateBtn();
    git.typeAndWaitNewBranchName(TEST_BRANCH);
    git.waitBranchInTheList(MASTER_BRANCH);
    git.waitBranchInTheList(TEST_BRANCH);
    git.closeBranchesForm();
  }

  private void switchOnTestBranch() {
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.BRANCHES);
    git.waitBranchInTheList(MASTER_BRANCH);
    git.waitBranchInTheList(TEST_BRANCH);
    git.selectBranchAndClickCheckoutBtn(TEST_BRANCH);
    loader.waitOnClosed();
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.BRANCHES);
    git.waitBranchInTheListWithCoState(TEST_BRANCH);
    git.closeBranchesForm();
    loader.waitOnClosed();
  }

  private void checkShwithConflict() {
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.BRANCHES);
    loader.waitOnClosed();
    git.waitBranchInTheList(MASTER_BRANCH);
    git.waitBranchInTheList(TEST_BRANCH);
    git.selectBranchAndClickCheckoutBtn(TEST_BRANCH);
    git.closeBranchesForm();
    git.waitGitStatusBarWithMess(CONFLICT_MESSAGE);
  }

  private void switchOnMasterBranch() {
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.BRANCHES);
    loader.waitOnClosed();
    git.waitBranchInTheList(MASTER_BRANCH);
    git.waitBranchInTheList(TEST_BRANCH);
    git.selectBranchAndClickCheckoutBtn(MASTER_BRANCH);
    loader.waitOnClosed();
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.BRANCHES);
    git.waitBranchInTheListWithCoState(MASTER_BRANCH);
    git.closeBranchesForm();
    loader.waitOnClosed();
  }
}
