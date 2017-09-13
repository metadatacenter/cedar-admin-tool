package org.metadatacenter.admin.task.importflatfolder;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.codec.net.URLCodec;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.metadatacenter.admin.task.importexport.ImportFileDescriptor;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.model.provenance.ProvenanceInfo;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.json.JsonUtils;
import org.metadatacenter.util.provenance.ProvenanceUtil;

import java.io.IOException;

import static org.metadatacenter.constant.CedarQueryParameters.QP_FOLDER_ID;
import static org.metadatacenter.constant.HttpConnectionConstants.CONNECTION_TIMEOUT;
import static org.metadatacenter.constant.HttpConnectionConstants.SOCKET_TIMEOUT;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

public class ImportWorker {

  private final CedarConfig cedarConfig;
  private final ProvenanceInfo provenanceInfo;
  private final CedarUser newOwner;
  private final ProvenanceUtil provenanceUtil;
  private final String folderId;
  private final AdminOutput out;

  public ImportWorker(AdminOutput out, CedarConfig cedarConfig, CedarUser newOwner, String folderId) {
    this.out = out;
    this.cedarConfig = cedarConfig;
    this.provenanceUtil = new ProvenanceUtil();
    this.provenanceInfo = provenanceUtil.build(newOwner);
    this.newOwner = newOwner;
    this.folderId = folderId;
  }

  public void importFile(ImportFileDescriptor descriptor) {
    out.println();
    JsonNode infoNode = null;
    try {
      infoNode = JsonMapper.MAPPER.readTree(descriptor.getInfo().toFile());
    } catch (IOException e) {
      out.error(e);
    }

    if (infoNode != null) {
      String nodeTypeString = infoNode.get("nodeType").asText();
      CedarNodeType nodeType = CedarNodeType.forValue(nodeTypeString);
      if (nodeType != null) {
        out.println(nodeType);

        JsonNode contentNode = null;
        try {
          contentNode = JsonMapper.MAPPER.readTree(descriptor.getContent().toFile());
        } catch (IOException e) {
          out.error(e);
        }

        if (contentNode != null) {
          JsonUtils.removeField(contentNode, "_id");
          JsonUtils.removeField(contentNode, "parentId");
          provenanceUtil.addProvenanceInfo(contentNode, provenanceInfo);
          JsonUtils.localizeAtIdsAndTemplateId(contentNode, cedarConfig.getLinkedDataUtil());
          out.println(contentNode);

          String authString = newOwner.getFirstApiKeyAuthHeader();
          try {
            String url = cedarConfig.getServers().getResource().getBase() + nodeType.getPrefix() +
                "?" + QP_FOLDER_ID + "=" + new URLCodec().encode(folderId);
            out.println("***IMPORT:" + url);
            Request request = Request.Post(url)
                .bodyString(contentNode.toString(), ContentType.APPLICATION_JSON)
                .connectTimeout(CONNECTION_TIMEOUT)
                .socketTimeout(SOCKET_TIMEOUT)
                .addHeader(HTTP_HEADER_AUTHORIZATION, authString);
            HttpResponse response = request.execute().returnResponse();
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_CREATED) {
              out.println("The document was created");
            } else {
              out.println("The document was not created");
            }
          } catch (Exception e) {
            out.error(e);
          }
        }

      }

    }
  }
}
