package org.metadatacenter.admin.task;

import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.config.CedarConfig;

import java.util.List;

public interface ICedarAdminTask {
  void setArguments(String[] args);

  void injectConfig(CedarConfig cedarConfig);

  void init();

  int execute();

  List<String> getDescription();

  List<String> getArguments();

  void setOutput(AdminOutput out);
}
