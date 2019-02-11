package org.metadatacenter.admin.task;

import org.apache.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

public class SearchGenerateEmptyIndex extends AbstractCedarAdminTaskWithAdminUser {

  public SearchGenerateEmptyIndex() {
    description.add("It makes a REST call to the Resource server to generate an empty Elasticsearch search index");
    description.add("Note that the Resource server must be running before executing this command");
  }

  private void generateEmptySearchIndex() {
    out.info("Requesting empty search index generation.");
    try {
      String url = cedarConfig.getServers().getResource().getGenerateEmptySearchIndex();
      Map<String, Object> requestMap = new HashMap<>();
      int statusCode = post(url, requestMap);
      if (statusCode == HttpStatus.SC_OK) {
        out.info(
            "The search empty index generation was successfully started. Please inspect the resource server log for " +
                "progress!");
      } else {
        out.error("Error while requesting empty index generation. HTTP status code: " + statusCode);
        out.error("The requested task was not completed!");
      }
    } catch (Exception e) {
      out.error("Error while generating empty search index", e);
      out.error("The requested task was not completed!");
    }
  }

  @Override
  public int execute() {
    generateEmptySearchIndex();
    return 0;
  }

}
