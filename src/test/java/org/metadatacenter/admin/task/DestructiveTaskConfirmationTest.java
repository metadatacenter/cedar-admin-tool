package org.metadatacenter.admin.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestructiveTaskConfirmationTest {

  @Test
  void folderPurgeUsesSharedConfirmationWithResolvedTargetAndCount() {
    TestFolderPurgeContent task = new TestFolderPurgeContent(true);

    assertTrue(task.confirm("Project folder", "https://repo.metadatacenter.org/folders/123", 17));
    assertEquals("Permanently deleting 17 child artifact(s) from folder 'Project folder' " +
        "(https://repo.metadatacenter.org/folders/123) in Neo4j and MongoDB...", task.confirmationMessage);
  }

  @Test
  void folderPurgeHonorsRejectedConfirmation() {
    TestFolderPurgeContent task = new TestFolderPurgeContent(false);

    assertFalse(task.confirm("Project folder", "https://repo.metadatacenter.org/folders/123", 17));
  }

  @Test
  void impexReplacementUsesSharedConfirmationWithDatabaseAndImportDirectory() {
    TestImpexImportAll task = new TestImpexImportAll(true);

    assertTrue(task.confirm("/data/import", "cedar"));
    assertEquals("Deleting every artifact document from MongoDB database 'cedar' and all CEDAR data from Neo4j " +
        "before importing from '/data/import'...", task.confirmationMessage);
  }

  @Test
  void impexReplacementHonorsRejectedConfirmation() {
    TestImpexImportAll task = new TestImpexImportAll(false);

    assertFalse(task.confirm("/data/import", "cedar"));
  }

  private static class TestFolderPurgeContent extends FolderPurgeContent {
    private final boolean confirmationResult;
    private String confirmationMessage;

    private TestFolderPurgeContent(boolean confirmationResult) {
      this.confirmationResult = confirmationResult;
    }

    private boolean confirm(String folderName, String folderId, int childArtifactCount) {
      return confirmPurge(folderName, folderId, childArtifactCount);
    }

    @Override
    protected boolean getConfirmInput(String message) {
      confirmationMessage = message;
      return confirmationResult;
    }
  }

  private static class TestImpexImportAll extends ImpexImportAll {
    private final boolean confirmationResult;
    private String confirmationMessage;

    private TestImpexImportAll(boolean confirmationResult) {
      this.confirmationResult = confirmationResult;
    }

    private boolean confirm(String importDir, String mongoDatabaseName) {
      return confirmReplacement(importDir, mongoDatabaseName);
    }

    @Override
    protected boolean getConfirmInput(String message) {
      confirmationMessage = message;
      return confirmationResult;
    }
  }
}
