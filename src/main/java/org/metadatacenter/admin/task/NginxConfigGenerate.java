package org.metadatacenter.admin.task;

import org.metadatacenter.config.ServerConfig;
import org.metadatacenter.model.ServerName;

public class NginxConfigGenerate extends AbstractCedarAdminTask {

  protected static final String BACKEND_AUTH_HTTPS = "backend-auth-https";
  protected static final String CEDAR_CEDAR_SSL_CONF = "cedar/cedar-ssl.conf;";
  protected static final String PROXY_SET_HEADER = "proxy_set_header";
  protected static final String SERVER_NAME = "server_name";
  protected static final String INCLUDE = "include";
  protected static final String ERROR_LOG = "error_log";
  protected static final String ACCESS_LOG = "access_log";
  protected static final String LISTEN = "listen";
  protected static final String LOCATION = "location";
  private String cedarHome;
  private boolean isLocal;
  private String host;
  private String logHome;
  private final static String tt = "\t\t";
  private final static String t = "\t";
  private final static String n = "\n";

  private enum ShowApiDoc {
    NO, YES
  }

  private enum Handle502Error {
    NO, YES
  }

  private enum AddSSL {
    NO, YES
  }

  private enum ProxyToProtocol {
    HTTP("http"), HTTPS("https");

    private String protocol;

    ProxyToProtocol(String protocol) {
      this.protocol = protocol;
    }

    public String getProtocol() {
      return protocol;
    }
  }

  public NginxConfigGenerate() {
    description.add("Generates nginx server config.");
  }

  @Override
  public void init() {
    cedarHome = cedarConfig.getHome();
    host = cedarConfig.getHost();
    logHome = cedarHome + "log/";
    isLocal = "metadatacenter.orgx".equals(host);
  }

  @Override
  public int execute() {
    StringBuilder sb = new StringBuilder();

    addCommonEntries(sb);
    rem(sb, "proxies");
    if (isLocal) {
      addUpstream(sb, "frontend", 4200);
    }

    for (ServerName sn : ServerName.values()) {
      ServerConfig serverConfig = cedarConfig.getServers().get(sn);
      addUpstream(sb, "backend-" + sn.getName(), serverConfig.getPort());
    }

    addUpstream(sb, BACKEND_AUTH_HTTPS, 8543);

    rem(sb, "no subdomain");
    addRedirectHttp(sb, host, "cedar." + host);
    addRedirectHttps(sb, host, "cedar." + host);

    rem(sb, "frontend");
    addRedirectHttp(sb, "cedar." + host, "cedar." + host);
    if (isLocal) {
      addServerWithProxy(sb, "cedar." + host, "frontend", "cedar-template-editor",
          ShowApiDoc.NO,
          Handle502Error.NO,
          ProxyToProtocol.HTTP);
    } else {
      addFrontend(sb, "cedar." + host, "cedar-template-editor");
    }

    for (ServerName sn : ServerName.values()) {
      ServerConfig serverConfig = cedarConfig.getServers().get(sn);
      String name = sn.getName();
      String domain = name + "." + host;
      rem(sb, name);
      addRedirectHttp(sb, domain, domain);
      addServerWithProxy(sb, domain, "backend-" + name, "cedar-" + name + "-server",
          serverConfig.isApiDoc() ? ShowApiDoc.YES : ShowApiDoc.NO,
          Handle502Error.YES,
          ProxyToProtocol.HTTP);
    }

    String name = "auth";
    rem(sb, name);
    String domain = name + "." + host;
    addRedirectHttp(sb, domain, domain);
    addServerWithProxy(sb, domain, BACKEND_AUTH_HTTPS, "cedar-" + name + "-server",
        ShowApiDoc.NO,
        Handle502Error.NO,
        ProxyToProtocol.HTTPS);

    out.warn("NGINX config file starts here --------------------------");
    System.out.println(sb);
    out.warn("NGINX config file ends here ----------------------------");
    return 0;
  }

  private void rem(StringBuilder sb, String msg) {
    sb.append("# ").append(msg).append(" --------------------------------------------------------").append(n).append(n);
  }

  private void addFrontend(StringBuilder sb, String serverName, String logFolder) {
    sb.append("server {").append(n);
    sb.append(t).append(LISTEN).append(tt).append("443 ssl;").append(n);
    sb.append(t).append(SERVER_NAME).append(tt).append(serverName).append(";").append(n);
    sb.append(t).append(INCLUDE).append(tt).append(CEDAR_CEDAR_SSL_CONF).append(n);

    sb.append(t).append(LOCATION).append(" / {").append(n);
    sb.append(t).append(t).append("if ($isMaintenance && !$isInternalNetwork) {").append(n);
    sb.append(t).append(t).append(t)
        .append("rewrite ^ http://uptime.statuscake.com/?TestID=rrcYEek524;").append(n);
    sb.append(t).append(t).append("}").append(n);

    sb.append(t).append(t).append("root").append(tt)
        .append(cedarHome).append("cedar-template-editor/app/;").append(n);
    sb.append(t).append(t).append("try_files").append(tt).append("$uri /index.html;").append(n);
    sb.append(t).append("}").append(n);
    sb.append(t).append(ERROR_LOG).append(tt)
        .append(logHome).append(logFolder).append("/nginx-error.log warn;").append(n);
    sb.append(t).append(ACCESS_LOG).append(tt)
        .append(logHome).append(logFolder).append("/nginx-access.log combined if=$loggable;").append(n);
    sb.append("}").append(n);
    sb.append(n);
  }

  private void addServerWithProxy(StringBuilder sb, String serverName, String proxyName, String logFolder, ShowApiDoc
      showApidoc, Handle502Error handle502Error, ProxyToProtocol proxyToProtocol) {
    sb.append("server {").append(n);
    sb.append(t).append(LISTEN).append(tt).append("443 ssl;").append(n);
    sb.append(t).append(SERVER_NAME).append(tt).append(serverName).append(";").append(n);
    sb.append(t).append(INCLUDE).append(tt).append(CEDAR_CEDAR_SSL_CONF).append(n);
    sb.append(t).append(LOCATION).append(" / {").append(n);
    sb.append(t).append(t).append("proxy_pass").append(tt).append(proxyToProtocol.getProtocol()).
        append("://cedar-").append(proxyName).append(";").append(n);
    sb.append(t).append("}").append(n);
    if (showApidoc == ShowApiDoc.YES) {
      sb.append(t).append(LOCATION).append(" /api {").append(n);
      sb.append(t).append(t).append("alias").append(tt).append(cedarHome).append("cedar-swagger-ui;").append(n);
      sb.append(t).append("}").append(n);
    }
    if (handle502Error == Handle502Error.YES) {
      sb.append(t).append("error_page").append(tt).append("502 /errors/502.json;").append(n);
      sb.append(t).append(LOCATION).append(tt).append("^~ /errors/ {").append(n);
      sb.append(t).append(t).append("internal;").append(n);
      sb.append(t).append(t).append("root").append(tt).append(cedarHome)
          .append("cedar-conf/static-content/localhost/;").append(n);
      sb.append(t).append("}").append(n);
    }
    sb.append(t).append(ERROR_LOG).append(tt)
        .append(logHome).append(logFolder).append("/nginx-error.log warn;").append(n);
    sb.append(t).append(ACCESS_LOG).append(tt)
        .append(logHome).append(logFolder).append("/nginx-access.log combined if=$loggable;").append(n);
    sb.append("}").append(n);
    sb.append(n);
  }

  private void addRedirectHttp(StringBuilder sb, String from, String to) {
    addRedirect(sb, from, to, 80, AddSSL.NO);
  }

  private void addRedirectHttps(StringBuilder sb, String from, String to) {
    addRedirect(sb, from, to, 443, AddSSL.YES);
  }

  private void addRedirect(StringBuilder sb, String from, String to, int port, AddSSL addSSL) {
    sb.append("server {").append(n);
    sb.append(t).append(LISTEN).append(tt).append(port).append(";").append(n);
    sb.append(t).append(SERVER_NAME).append(tt).append(from).append(";").append(n);
    if (addSSL == AddSSL.YES) {
      sb.append(t).append(INCLUDE).append(tt).append(CEDAR_CEDAR_SSL_CONF).append(n);
    }
    sb.append(t).append("return").append(tt).append("301 https://")
        .append(to).append("$request_uri;").append(n);
    sb.append("}").append(n);
    sb.append(n);
  }

  private void addUpstream(StringBuilder sb, String shortName, int port) {
    sb.append("upstream cedar-").append(shortName).append(" {").append(n);
    sb.append(t).append("server 127.0.0.1:").append(port).append(";").append(n);
    sb.append("}").append(n);
    sb.append(n);
  }

  private void addCommonEntries(StringBuilder sb) {
    sb.append(ERROR_LOG).append(tt).append(logHome).append("nginx/nginx-error.log;").append(n);
    sb.append(n);
    sb.append("proxy_http_version").append(tt).append("1.1; #this is essential for chunked responses").append(n);
    sb.append("proxy_buffering").append(tt).append("off;").append(n);
    sb.append(PROXY_SET_HEADER).append(tt).append("X-Real-IP $remote_addr;").append(n);
    sb.append(PROXY_SET_HEADER).append(tt).append("X-Scheme $scheme;").append(n);
    sb.append(PROXY_SET_HEADER).append(tt).append("X-Forwarded-For $proxy_add_x_forwarded_for;").append(n);
    sb.append(PROXY_SET_HEADER).append(tt).append("Host $http_host;").append(n);
    sb.append("proxy_intercept_errors").append(tt).append("on;").append(n);
    sb.append(n);
    sb.append("map $status $loggable {").append(n);
    sb.append(t).append("~^[3]  0;").append(n);
    sb.append(t).append("default 1;").append(n);
    sb.append("}").append(n);
    sb.append(n);
  }

}
