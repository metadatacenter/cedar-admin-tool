package org.metadatacenter.admin.task;

import org.metadatacenter.admin.config.CedarConfig;

public interface CedarAdminTask {

  void setArguments(String[] args);

  void init(CedarConfig config);

  int execute();

}
