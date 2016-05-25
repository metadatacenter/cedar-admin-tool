package org.metadatacenter.admin.task;


import org.metadatacenter.config.CedarConfig;

import java.util.List;

public interface CedarAdminTask {

  void setArguments(String[] args);

  void init(CedarConfig config);

  int execute();

  List<String> getDescription();

}
