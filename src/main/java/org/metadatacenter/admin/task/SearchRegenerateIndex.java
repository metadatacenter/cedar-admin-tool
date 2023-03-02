package org.metadatacenter.admin.task;

import org.apache.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

public class SearchRegenerateIndex extends AbstractCedarAdminTaskWithAdminUser {

  public SearchRegenerateIndex() {
    description.add("It makes a REST call to the Resource server to regenerate the Elasticsearch search index");
    description.add("Note that the Resource server must be running before executing this command");
  }

  private void regenerateSearchIndex(boolean force) {
    out.info("Requesting search index regeneration. Force:" + force);
    try {
      String url = cedarConfig.getServers().getResource().getRegenerateSearchIndex();
      Map<String, Object> requestMap = new HashMap<>();
      requestMap.put("force", force);
      int statusCode = post(url, requestMap);
      if (statusCode == HttpStatus.SC_OK) {
        out.info("The search index regeneration was successfully started. Please inspect the resource server log for progress!");
      } else {
        out.error("Error while requesting index regeneration. HTTP status code: " + statusCode);
        out.error("The requested task was not completed!");
      }
    } catch (Exception e) {
      out.error("Error while regenerating search index", e);
      out.error("The requested task was not completed!");
    }
  }

  @Override
  public int execute() {
    regenerateSearchIndex(true);
    return 0;
  }

}
