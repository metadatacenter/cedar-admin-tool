package org.metadatacenter.admin.task;

import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.config.CedarConfig;

import java.util.List;

public interface CedarAdminTask {
  void setArguments(String[] args);

  void init(CedarConfig config);

  int execute();

  List<String> getDescription();

  List<String> getArguments();

  void setOutput(AdminOutput out);
}
