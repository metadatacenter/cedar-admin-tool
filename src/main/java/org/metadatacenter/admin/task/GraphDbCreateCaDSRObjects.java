package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.UserServiceSession;
import org.metadatacenter.server.security.model.user.CedarSuperRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.util.CedarUserUtil;
import org.metadatacenter.server.service.UserService;

import java.util.List;

public class GraphDbCreateCaDSRObjects extends AbstractNeo4JAccessTask {

  public GraphDbCreateCaDSRObjects() {
    description.add("Creates caDSR top category");
    description.add("Creates 'cadsr-admin' user");
    description.add("Grants 'write' permission to the user regarding the category");
  }

  @Override
  public void init() {
    super.init();
  }

  @Override
  public int execute() {
    AdminServiceSession adminSession = createUnconditionalCedarAdminSession(cedarConfig);

    UserRepresentation caDSRAdminRepresentation = null;

    List<UserRepresentation> userRepresentations = listAllUsersFromKeycloak();
    for (UserRepresentation ur : userRepresentations) {
      if (cedarConfig.getCaDSRAdminUserConfig().getUserName().equals(ur.getUsername())) {
        caDSRAdminRepresentation = ur;
      }
    }

    CedarUser caDSRAdminExtract = new CedarUser();
    caDSRAdminExtract.setFirstName(caDSRAdminRepresentation.getFirstName());
    caDSRAdminExtract.setLastName(caDSRAdminRepresentation.getLastName());
    caDSRAdminExtract.setEmail(caDSRAdminRepresentation.getEmail());
    caDSRAdminExtract.setId(linkedDataUtil.getUserId(caDSRAdminRepresentation.getId()));

    CedarUser caDSRAdminCandidate = CedarUserUtil.createUserFromBlueprint(cedarConfig.getBlueprintUserProfile(), caDSRAdminExtract,
        CedarSuperRole.NORMAL, cedarConfig, cedarConfig.getCaDSRAdminUserConfig().getUserName());

    UserService userService = getNeoUserService();

    CedarUser existingUser = userService.findUser(caDSRAdminCandidate.getResourceId());
    if (existingUser == null) {
      existingUser = userService.createUser(caDSRAdminCandidate);
    }

    CedarRequestContext userRequestContext;
    try {
      userRequestContext = CedarRequestContextFactory.fromUser(existingUser);
      UserServiceSession userSession = CedarDataServices.getUserServiceSession(userRequestContext);
      adminSession.ensureCaDSRObjectsExists(existingUser, userSession);
    } catch (CedarAccessException e) {
      out.error("Error while creating caDSR admin user.", e);
    }
    return 0;
  }
}
