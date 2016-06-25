package org.metadatacenter.admin.task.importflatfolder;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.codec.net.URLCodec;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.metadatacenter.admin.task.importexport.ImportFileDescriptor;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.HttpConnectionConstants;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.model.provenance.ProvenanceInfo;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.json.JsonUtils;
import org.metadatacenter.util.provenance.ProvenanceUtil;

import java.io.IOException;

public class ImportWorker {

  private CedarConfig cedarConfig;
  private ProvenanceInfo provenanceInfo;
  private CedarUser newOwner;
  private String folderId;

  public ImportWorker(CedarConfig cedarConfig, String userUUID, CedarUser newOwner, String folderId) {
    this.cedarConfig = cedarConfig;
    this.provenanceInfo = ProvenanceUtil.buildFromUUID(cedarConfig, userUUID);
    this.newOwner = newOwner;
    this.folderId = folderId;
  }

  public void importFile(String name, ImportFileDescriptor descriptor) {
    System.out.println();
    JsonNode infoNode = null;
    try {
      infoNode = JsonMapper.MAPPER.readTree(descriptor.getInfo().toFile());
    } catch (IOException e) {
      e.printStackTrace();
    }

    if (infoNode != null) {
      String nodeTypeString = infoNode.get("nodeType").asText();
      CedarNodeType nodeType = CedarNodeType.forValue(nodeTypeString);
      if (nodeType != null) {
        System.out.println(nodeType);

        JsonNode contentNode = null;
        try {
          contentNode = JsonMapper.MAPPER.readTree(descriptor.getContent().toFile());
        } catch (IOException e) {
          e.printStackTrace();
        }

        if (contentNode != null) {
          JsonUtils.removeField(contentNode, "_id");
          JsonUtils.removeField(contentNode, "parentId");
          ProvenanceUtil.addProvenanceInfo(contentNode, provenanceInfo);
          JsonUtils.localizeAtIdsAndTemplateId(contentNode, cedarConfig.getLinkedDataUtil());
          System.out.println(contentNode);

          String apiKey = newOwner.getFirstActiveApiKey();
          String authString = HttpConstants.HTTP_AUTH_HEADER_APIKEY_PREFIX + apiKey;
          try {
            String url = cedarConfig.getServers().getResource().getBase() + nodeType.getPrefix() +
                "?importMode=true&folderId=" + new URLCodec().encode(folderId);
            System.out.println("***IMPORT:" + url);
            Request request = Request.Post(url)
                .bodyString(contentNode.toString(), ContentType.APPLICATION_JSON)
                .connectTimeout(HttpConnectionConstants.CONNECTION_TIMEOUT)
                .socketTimeout(HttpConnectionConstants.SOCKET_TIMEOUT)
                .addHeader(HttpHeaders.AUTHORIZATION, authString);
            HttpResponse response = request.execute().returnResponse();
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_CREATED) {
              System.out.println("The document was created");
            } else {
              System.out.println("The document was not created");
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
        }

      }

    }
  }
}
