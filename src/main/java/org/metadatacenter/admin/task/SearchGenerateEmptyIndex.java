package org.metadatacenter.admin.task;

import org.apache.hc.core5.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

public class SearchGenerateEmptyIndex extends AbstractCedarAdminTaskWithAdminUser {

  public SearchGenerateEmptyIndex() {
    description.add("It makes a REST call to the Resource server to generate an empty Elasticsearch search index");
    description.add("Note that the Resource server must be running before executing this command");
  }

  private boolean generateEmptySearchIndex() {
    out.info("Requesting empty search index generation.");
    try {
      String url = cedarConfig.getServers().getResource().getGenerateEmptySearchIndex();
      Map<String, Object> requestMap = new HashMap<>();
      int statusCode = post(url, requestMap);
      if (statusCode == HttpStatus.SC_OK) {
        out.info("The search empty index generation was successfully started. Please inspect the resource server log for progress!");
        return true;
      } else {
        out.error("Error while requesting empty index generation. HTTP status code: " + statusCode);
        out.error("The requested task was not completed!");
      }
    } catch (Exception e) {
      out.error("Error while generating empty search index", e);
      out.error("The requested task was not completed!");
    }
    return false;
  }

  @Override
  public int execute() {
    return generateEmptySearchIndex() ? 0 : -1;
  }

}
