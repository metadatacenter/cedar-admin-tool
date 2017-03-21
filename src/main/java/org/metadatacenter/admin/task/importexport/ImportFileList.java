package org.metadatacenter.admin.task.importexport;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ImportFileList {

  private final Map<String, ImportFileDescriptor> files;

  public ImportFileList() {
    files = new HashMap<>();
  }

  public void add(Path path) {
    String name = path.getFileName().toString();
    if (name.endsWith(ImportExportConstants.INFO_SUFFIX)) {
      String justName = name.substring(0, name.length() - ImportExportConstants.INFO_SUFFIX.length());
      registerInfo(path, justName);
    }
    if (name.endsWith(ImportExportConstants.CONTENT_SUFFIX)) {
      String justName = name.substring(0, name.length() - ImportExportConstants.CONTENT_SUFFIX.length());
      registerContent(path, justName);
    }
  }

  private void registerInfo(Path path, String name) {
    ensureNameExists(name);
    files.get(name).setInfo(path);
  }

  private void registerContent(Path path, String name) {
    ensureNameExists(name);
    files.get(name).setContent(path);
  }

  private void ensureNameExists(String name) {
    if (!files.containsKey(name)) {
      files.put(name, new ImportFileDescriptor());
    }
  }

  public Map<String, ImportFileDescriptor> getFiles() {
    return Collections.unmodifiableMap(files);
  }
}
