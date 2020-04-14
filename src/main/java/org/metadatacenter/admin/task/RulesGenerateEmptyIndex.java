package org.metadatacenter.admin.task;

import org.apache.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

public class RulesGenerateEmptyIndex extends AbstractCedarAdminTaskWithAdminUser {

  public RulesGenerateEmptyIndex() {
    description.add("It makes a REST call to the Resource server to generate an empty Elasticsearch rules index");
    description.add("Note that the Resource server must be running before executing this command");
  }

  private void generateEmptyRulesIndex() {
    out.info("Requesting empty rules index generation.");
    try {
      String url = cedarConfig.getServers().getResource().getGenerateEmptyRulesIndex();
      Map<String, Object> requestMap = new HashMap<>();
      int statusCode = post(url, requestMap);
      if (statusCode == HttpStatus.SC_OK) {
        out.info("The rules empty index generation was successfully started. Please inspect the resource server log for progress!");
      } else {
        out.error("Error while requesting empty index generation. HTTP status code: " + statusCode);
        out.error("The requested task was not completed!");
      }
    } catch (Exception e) {
      out.error("Error while generating empty rules index", e);
      out.error("The requested task was not completed!");
    }
  }

  @Override
  public int execute() {
    generateEmptyRulesIndex();
    return 0;
  }

}
