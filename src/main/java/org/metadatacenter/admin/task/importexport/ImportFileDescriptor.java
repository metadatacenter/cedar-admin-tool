package org.metadatacenter.admin.task.importexport;

import java.nio.file.Path;

public class ImportFileDescriptor {

  private Path content;
  private Path info;

  public ImportFileDescriptor() {
  }

  public Path getContent() {
    return content;
  }

  public void setContent(Path content) {
    this.content = content;
  }

  public Path getInfo() {
    return info;
  }

  public void setInfo(Path info) {
    this.info = info;
  }

  public boolean isComplete() {
    return content != null && info != null;
  }

  @Override
  public String toString() {
    return "{" +
        "content: " +
        (content == null ? "null" : content.toFile().getName()) +
        ", " +
        "info: " +
        (info == null ? "null" : info.toFile().getName()) +
        "}";
  }
}
