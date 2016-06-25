package org.metadatacenter.admin.task.importflatfolder;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.metadatacenter.admin.task.AbstractNeo4JAccessTask;
import org.metadatacenter.admin.task.importexport.ImportFileDescriptor;
import org.metadatacenter.admin.task.importexport.ImportFileList;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.service.mongodb.UserServiceMongoDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

public class ImportFlatFolder extends AbstractNeo4JAccessTask {

  private CedarConfig cedarConfig;
  private Neo4JUserSession adminNeo4JSession;
  private UserService userService;

  private Logger logger = LoggerFactory.getLogger(ImportFlatFolder.class);

  public ImportFlatFolder() {
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
  public void init(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  @Override
  public int execute() {
    if (arguments.size() != 4) {
      System.out.println("You need to specify all 3 arguments");
      return -1;
    }

    String sourceFolder = arguments.get(1);
    String folderId = arguments.get(2);
    String userUUID = arguments.get(3);

    System.out.println("Input parameters:");
    System.out.println("sourceFolder: " + sourceFolder);
    System.out.println("folderId    : " + folderId);
    System.out.println("userUUID    : " + userUUID);

    Path sourcePath = Paths.get(sourceFolder);
    File sourceDir = sourcePath.toFile();
    if (!sourceDir.exists()) {
      System.out.println("The sourceFolder does not exist!");
      return -2;
    }
    if (!sourceDir.isDirectory()) {
      System.out.println("The local source folder specified by sourceFolder is not a folder!");
      return -3;
    }

    adminNeo4JSession = buildCedarAdminNeo4JSession(cedarConfig, false);

    CedarFSFolder targetFolder = adminNeo4JSession.findFolderById(folderId);
    if (targetFolder == null) {
      System.out.println("The remote target folder specified by folderId does not exist!");
      return -4;
    }

    String mongoDatabaseName = cedarConfig.getMongoConfig().getDatabaseName();
    String usersCollectionName = cedarConfig.getMongoConfig().getCollections().get(CedarNodeType.USER.getValue());

    userService = new UserServiceMongoDB(mongoDatabaseName, usersCollectionName);
    CedarUser newOwner = null;
    try {
      newOwner = userService.findUser(userUUID);
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ProcessingException e) {
      e.printStackTrace();
    }

    if (newOwner == null) {
      System.out.println("The new owner specified by userId does not exist!");
      return -5;
    }

    /*String adminUserUUID = null;
    CedarUser adminUser = null;
    try {
      adminUserUUID = cedarConfig.getKeycloakConfig().getAdminUser().getUuid();
      adminUser = userService.findUser(adminUserUUID);
    } catch (Exception e) {
      logger.error("Error while loading admin user by UUID:" + adminUserUUID);
    }

    if (adminUser == null) {
      System.out.println("The cedar-admin user can not be loaded!");
      return -6;
    }*/

    ImportFileList importList = new ImportFileList();

    try (final Stream<Path> stream = Files.list(sourcePath)) {
      stream
          .filter(path -> path.toFile().isFile())
          .forEach(path -> {
            importList.add(path);
          });
    } catch (IOException e) {
      e.printStackTrace();
    }

    ImportWorker importWorker = new ImportWorker(cedarConfig, userUUID, newOwner, folderId);

    for(Map.Entry<String, ImportFileDescriptor> ifd : importList.getFiles().entrySet()) {
      ImportFileDescriptor desc = ifd.getValue();
      if (desc.isComplete()) {
        System.out.println("Importing :" + desc);
        importWorker.importFile(ifd.getKey(), desc);
      } else {
        System.out.println("Skipping  :" + desc);
      }
    }

    return 0;
  }


}