package org.metadatacenter.admin.task;

import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.config.CedarConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractCedarAdminTask implements ICedarAdminTask {

  protected List<String> arguments;
  protected List<String> description = new ArrayList<>();
  protected AdminOutput out;
  protected CedarConfig cedarConfig;
  public static final String CONFIRM = "confirm";

  @Override
  public void setArguments(String[] args) {
    arguments = new ArrayList<>();
    arguments.addAll(Arrays.asList(args));
  }

  @Override
  public void injectConfig(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  @Override
  public List<String> getDescription() {
    return description;
  }

  public List<String> getArguments() {
    return arguments;
  }

  @Override
  public void setOutput(AdminOutput out) {
    this.out = out;
  }
}