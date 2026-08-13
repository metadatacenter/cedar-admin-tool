package org.metadatacenter.admin.task;

import org.apache.hc.core5.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated The endpoint behind this command empties the rules index rather than regenerating it. The rules index
 * holds association rules mined by the value recommender from template instances, which the resource server cannot
 * produce. Use {@link RulesGenerateEmptyIndex} when emptying the index is the intent, and regenerate rules through
 * the value recommender's generate-rules command.
 */
@Deprecated
public class RulesRegenerateIndex extends AbstractCedarAdminTaskWithAdminUser {

  public RulesRegenerateIndex() {
    description.add("DEPRECATED: this command empties the rules index, it does not regenerate it");
    description.add("It makes a REST call to the Resource server to regenerate the Elasticsearch rules index");
    description.add("Note that the Resource server must be running before executing this command");
  }

  private void regenerateRulesIndex(boolean force) {
    out.warn("This command is deprecated: it empties the rules index instead of regenerating it.");
    out.warn("Every mined rule will be discarded and recommendations will stop returning results.");
    out.info("Requesting rules index regeneration. Force:" + force);
    try {
      String url = cedarConfig.getServers().getResource().getRegenerateRulesIndex();
      Map<String, Object> requestMap = new HashMap<>();
      requestMap.put("force", force);
      int statusCode = post(url, requestMap);
      if (statusCode == HttpStatus.SC_OK) {
        out.info("The rules index regeneration was successfully started. Please inspect the resource server log for progress!");
      } else {
        out.error("Error while requesting index regeneration. HTTP status code: " + statusCode);
        out.error("The requested task was not completed!");
      }
    } catch (Exception e) {
      out.error("Error while regenerating rules index", e);
      out.error("The requested task was not completed!");
    }
  }

  @Override
  public int execute() {
    regenerateRulesIndex(true);
    return 0;
  }

}
