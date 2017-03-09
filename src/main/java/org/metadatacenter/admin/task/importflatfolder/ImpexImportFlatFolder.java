package org.metadatacenter.admin.task.importflatfolder;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.metadatacenter.admin.task.AbstractNeo4JAccessTask;
import org.metadatacenter.admin.task.importexport.ImportFileDescriptor;
import org.metadatacenter.admin.task.importexport.ImportFileList;
import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.model.folderserver.FolderServerFolder;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

public class ImpexImportFlatFolder extends AbstractNeo4JAccessTask {

  private UserService userService;

  public ImpexImportFlatFolder() {
    description.add("Imports the contents of a local folder into a virtual folder using a given owner");
    description.add("Parameters:");
    description.add(" 1) Filesystem path to a folder.");
    description.add("    The contents of this folder will be imported.");
    description.add("    No recursion is performed, just the elements, templates and instances are handled");
    description.add(" 2) FolderId (url style) from the running system.");
    description.add("    The content will be created here");
    description.add(" 3) UserUUID (UUID style id) from the running system.");
    description.add("    This user will become the owner, the creator and the updater of the imported data.");
  }

  @Override
  public void init() {

  }

  @Override
  public int execute() {
    if (arguments.size() != 4) {
      out.warn("You need to specify all 3 arguments");
      return -1;
    }

    String sourceFolder = arguments.get(1);
    String folderId = arguments.get(2);
    String userId = arguments.get(3);

    out.println("Input parameters:");
    out.println("sourceFolder: " + sourceFolder);
    out.println("folderId    : " + folderId);
    out.println("userId    : " + userId);

    Path sourcePath = Paths.get(sourceFolder);
    File sourceDir = sourcePath.toFile();
    if (!sourceDir.exists()) {
      out.error("The sourceFolder does not exist!");
      return -2;
    }
    if (!sourceDir.isDirectory()) {
      out.error("The local source folder specified by sourceFolder is not a folder!");
      return -3;
    }
    FolderServiceSession folderSession = null;
    try {
      folderSession = createCedarFolderSession(cedarConfig);
    } catch (CedarAccessException e) {
      e.printStackTrace();
      return -6;
    }

    FolderServerFolder targetFolder = folderSession.findFolderById(folderId);
    if (targetFolder == null) {
      out.error("The remote target folder specified by folderId does not exist!");
      return -4;
    }

    userService = getUserService();
    CedarUser newOwner = null;
    try {
      newOwner = userService.findUser(userId);
    } catch (IOException | ProcessingException e) {
      out.error(e);
    }

    if (newOwner == null) {
      out.error("The new owner specified by userId does not exist!");
      return -5;
    }

    ImportFileList importList = new ImportFileList();

    try (final Stream<Path> stream = Files.list(sourcePath)) {
      stream
          .filter(path -> path.toFile().isFile())
          .forEach(path -> {
            importList.add(path);
          });
    } catch (IOException e) {
      out.error(e);
    }

    ImportWorker importWorker = new ImportWorker(out, cedarConfig, newOwner, folderId);

    for (Map.Entry<String, ImportFileDescriptor> ifd : importList.getFiles().entrySet()) {
      ImportFileDescriptor desc = ifd.getValue();
      if (desc.isComplete()) {
        out.info("Importing :" + desc);
        importWorker.importFile(desc);
      } else {
        out.info("Skipping  :" + desc);
      }
    }

    return 0;
  }


}