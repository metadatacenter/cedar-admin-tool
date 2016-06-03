package org.metadatacenter.admin.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractCedarAdminTask implements CedarAdminTask {

  protected List<String> arguments;
  protected List<String> description = new ArrayList<>();

  @Override
  public void setArguments(String[] args) {
    arguments = new ArrayList<>();
    arguments.addAll(Arrays.asList(args));
  }

  @Override
  public List<String> getDescription() {
    return description;
  }

  public List<String> getArguments() {
    return arguments;
  }
}