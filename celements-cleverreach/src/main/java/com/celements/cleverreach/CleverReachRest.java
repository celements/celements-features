package com.celements.cleverreach;

import static com.celements.model.util.References.*;
import static com.google.common.base.Preconditions.*;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.annotate.JsonTypeInfo.As;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.context.Execution;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

import com.celements.auth.classes.RemoteLoginClass;
import com.celements.cleverreach.exception.CleverReachRequestFailedException;
import com.celements.css.exception.CssInlineException;
import com.celements.model.access.IModelAccessFacade;
import com.celements.model.access.exception.DocumentNotExistsException;
import com.celements.model.classes.ClassDefinition;
import com.celements.model.context.ModelContext;
import com.celements.model.object.xwiki.XWikiObjectFetcher;
import com.google.common.collect.ImmutableList;
import com.xpn.xwiki.objects.BaseObject;

@Component(CleverReachRest.COMPONENT_NAME)
@InstantiationStrategy(ComponentInstantiationStrategy.SINGLETON)
public class CleverReachRest implements CleverReachService {

  private static final String RESPONSE_NO_BODY_LOGGING_MESSAGE = "[no body]";

  private static final Logger LOGGER = LoggerFactory.getLogger(CleverReachRest.class);

  public static final String COMPONENT_NAME = "rest";

  public static final String REST_CONFIG_SPACE_NAME = "Configs";
  public static final String REST_CONFIG_DOC_NAME = "CleverReachRest";
  protected static final String DEFAULT_REST_URL = "https://rest.cleverreach.com/";
  static final String PATH_VERSION = "v3/";
  static final String PATH_LOGIN = "oauth/token.php";
  static final String PATH_MAILINGS = "mailings.json/";
  static final String PATH_RECEIVERS = "receivers.json/";
  static final String SUBPATH_ATTRIBUTES = "/attributes";
  static final String PATH_WHOAMI = "debug/whoami.json";
  static final String PATH_TTL = "debug/ttl.json";

  static final String CONTEXT_CONNECTION_KEY = "clever_reach_connection";

  private static Pattern PATTERN_SUCCESS_RESP = Pattern.compile(".*\"success\".{0,1}:.{0,1}true.*");

  enum SubmitMethod {
    GET, POST, PUT, DELETE
  }

  @Requirement
  private IModelAccessFacade modelAccess;

  @Requirement
  private ModelContext modelContext;

  @Requirement
  private FailNotificationHandlerRole failNotify;

  @Requirement(RemoteLoginClass.CLASS_DEF_HINT)
  private ClassDefinition remoteLoginClass;

  @Requirement
  private Execution execution;

  @Requirement
  private IRestClientFactoryRole clientFactory;

  @Override
  public boolean updateMailing(@NotNull MailingConfig mailing) throws IOException {
    checkNotNull(mailing);
    if (updateMailingInternal(mailing)) {
      Response response = sendRestRequest(PATH_RECEIVERS + mailing.getReferenceUserId()
          + SUBPATH_ATTRIBUTES + "/" + mailing.getReferenceAttributeId(), new Value("1"),
          SubmitMethod.PUT);
      boolean isReadyToSend = isReadyToSendPut(response);
      if (!isReadyToSend) {
        failNotify.send("Update worked, but setting [ready to send] = true failed.",
            new IllegalStateException("Setting ready to send flag failed"));
      }
      return isReadyToSend;
    }
    return false;
  }

  @Override
  public boolean updateMailingRehearsal(@NotNull MailingConfig mailing) throws IOException {
    checkNotNull(mailing);
    MultivaluedHashMap<String, String> map = new MultivaluedHashMap<>();
    map.put("group_id", ImmutableList.of(mailing.getReferenceGroupId()));
    Response response = sendRestRequest(PATH_RECEIVERS + mailing.getReferenceUserId()
        + SUBPATH_ATTRIBUTES, map, SubmitMethod.GET);
    if (!isReadyToSendGet(response, mailing.getServerClass())) {
      return updateMailingInternal(mailing);
    } else {
      LOGGER.warn("REHEARSAL STOPPED: mailing is ready to send!");
      failNotify.send("Rehearsal failed since Newsletter is still \"Ready To Send\". Check if it "
          + "was sent correctly", new IllegalStateException("Newsletter is ready to send"));
    }
    return false;
  }

  private boolean updateMailingInternal(MailingConfig mailingConf) throws IOException {
    try {
      Response response = sendRestRequest(PATH_MAILINGS + mailingConf.getId(), buildMailing(
          mailingConf), SubmitMethod.PUT);
      LOGGER.info("Mailing update response [{}]", response);
      if ((response != null) && response.hasEntity()) {
        String content = response.readEntity(String.class);
        LOGGER.info("Mailing update response content [{}]", content);
        if (content.contains(mailingConf.getId()) && PATTERN_SUCCESS_RESP.matcher(content)
            .matches()) {
          return true;
        }
        LOGGER.warn("Mailing update not successful. Response content is [{}]", content);
      } else {
        LOGGER.warn("Mailing update failed with response [{}] and response hasEntity [{}]",
            response, (response != null) && response.hasEntity());
      }
    } catch (CssInlineException cie) {
      LOGGER.error("Exception while inlining CSS", cie);
      failNotify.send("Inlining the CSS failed!", cie);
    }
    return false;
  }

  Mailing buildMailing(MailingConfig mailingConf) throws CssInlineException {
    Mailing formData = new Mailing();
    formData.subject = mailingConf.getSubject();
    formData.content.html = mailingConf.getContentHtmlCssInlined();
    formData.content.text = mailingConf.getContentPlain();
    return formData;
  }

  @Override
  public String whoami() throws IOException {
    return runDebugRequest(PATH_WHOAMI);
  }

  @Override
  public String ttl() throws IOException {
    return runDebugRequest(PATH_TTL);
  }

  @Override
  public DocumentReference getConfigDocRef() {
    return create(DocumentReference.class, REST_CONFIG_DOC_NAME, create(SpaceReference.class,
        REST_CONFIG_SPACE_NAME, modelContext.getWikiRef()));
  }

  Response sendRestRequest(String path, Object data, SubmitMethod method) throws IOException {
    CleverReachConnection connection = getConnection();
    if (connection.isConnected()) {
      CleverReachToken token = connection.getToken().get();
      method = (method != null) ? method : SubmitMethod.POST;
      String authHeader = token.getTokenType() + " " + token.getToken();
      if (data instanceof MultivaluedMap) {
        getMultivalueMapFromOjb(data).add("token", token.getToken());
      }
      String completePath = PATH_VERSION + path;
      Response response = sendRequest(completePath, data, authHeader, method, connection);
      if (response.getStatus() == 200) {
        return response;
      } else {
        String errorMailMsg = "Request response status != 200. Status [" + response.getStatus()
            + "], Path [" + completePath + "], Method [" + method + "], Data [" + response + "], "
            + "Response [" + data + "]";
        LOGGER.info(errorMailMsg);
        String responseBody = RESPONSE_NO_BODY_LOGGING_MESSAGE;
        if (response.hasEntity()) {
          responseBody = response.readEntity(String.class);
          LOGGER.info("Response content [{}]", responseBody);
        }
        throw new CleverReachRequestFailedException(errorMailMsg, response, responseBody);
      }
    } else {
      throw new CleverReachRequestFailedException("Failed to connect", null,
          RESPONSE_NO_BODY_LOGGING_MESSAGE);
    }
  }

  CleverReachToken initializeToken(Response response, String jsonResponse) {
    ObjectMapper objMapper = new ObjectMapper();
    try {
      CleverReachToken token = objMapper.readValue(jsonResponse.getBytes(), CleverReachToken.class);
      if (token.isValid()) {
        LOGGER.trace("new token received [{}]", token);
        return token;
      } else {
        LOGGER.warn("Unable to receive token.\nResponse [{}]\nJSON-Response [{}]", response, jsonResponse);
      }
    } catch (IOException ioe) {
      LOGGER.warn("IOException caught. Unable to connect and receive token. Response [{}].",
          response);
      if (LOGGER.isTraceEnabled()) {
        // Workaround to log stack trace in trace despite NoStackTracePatternLayout
        LOGGER.error("Exception Reason", ioe);
      }
    }
    return null;
  }

  CleverReachConnection getConnection() throws CleverReachRequestFailedException {
    CleverReachConnection cachedConnection = (CleverReachConnection) execution.getContext()
        .getProperty(CONTEXT_CONNECTION_KEY);
    if ((null == cachedConnection) || !cachedConnection.isConnected()) {
      Optional<BaseObject> configObj = Optional.empty();
      try {
        configObj = XWikiObjectFetcher.on(modelAccess.getDocument(getConfigDocRef())).filter(
            remoteLoginClass).first().toJavaUtil();
      } catch (DocumentNotExistsException dnee) {
        LOGGER.warn("Document XWikiPreferences does not exist", dnee);
      }
      cachedConnection = buildConnection(configObj);
      execution.getContext().setProperty(CONTEXT_CONNECTION_KEY, cachedConnection);
    }
    return cachedConnection;
  }

  CleverReachConnection buildConnection(Optional<BaseObject> configObj)
      throws CleverReachRequestFailedException {
    if (configObj.isPresent()) {
      Optional<String> restBaseUrl = modelAccess.getFieldValue(configObj.get(),
          RemoteLoginClass.FIELD_URL).toJavaUtil();
      Optional<String> clientId = modelAccess.getFieldValue(configObj.get(),
          RemoteLoginClass.FIELD_USERNAME).toJavaUtil();
      Optional<String> clientSecret = modelAccess.getFieldValue(configObj.get(),
          RemoteLoginClass.FIELD_PASSWORD).toJavaUtil();
      if (clientId.isPresent() && clientSecret.isPresent()) {
        return new CleverReachConnection(restBaseUrl.orElse(""), login(restBaseUrl.orElse(null),
            clientId.get(), clientSecret.get()));
      }
    }
    LOGGER.warn("No config found on {}.{}", REST_CONFIG_SPACE_NAME, REST_CONFIG_DOC_NAME);
    return new CleverReachConnection();
  }

  CleverReachToken login(String restBaseUrl, String clientId, String clientSecret)
      throws CleverReachRequestFailedException {
    MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
    formData.putSingle("grant_type", "client_credentials");
    String authHeader = "Basic " + Base64.getEncoder().encodeToString(
        (clientId + ":" + clientSecret).getBytes());
    Response response = sendRequest(PATH_LOGIN, formData, authHeader, SubmitMethod.POST,
        restBaseUrl);
    boolean hasContent = response.hasEntity();
    String content = null;
    if (hasContent) {
      // readEntity closes the response stream, so it can only be read once
      content = response.readEntity(String.class);
    }
    traceLogLoginResponse(response, content);
    if (hasContent) {
      return initializeToken(response, content);
    } else {
      throw new CleverReachRequestFailedException("Unable to connect and receive token. Response "
          + "[{}]", response, RESPONSE_NO_BODY_LOGGING_MESSAGE);
    }
  }

  Response sendRequest(String path, Object data, String authHeader, SubmitMethod method,
      CleverReachConnection connection) throws CleverReachRequestFailedException {
    return sendRequest(path, data, authHeader, method, connection.getBaseUrl());
  }

  boolean isReadyToSendPut(Response response) {
    if ((response != null) && response.hasEntity()) {
      String content = response.readEntity(String.class);
      try {
        return Optional.ofNullable(new ObjectMapper()
            .readValue(content, ResponseBodyObj.class))
            .map(responseObj -> "1".equals(responseObj.value))
            .orElse(false);
      } catch (IOException ioe) {
        LOGGER.warn("Parsing CleverReach response to JSON failed. Content [{}]", content, ioe);
      }
    } else {
      LOGGER.warn("Mailing update failed with response [{}]", response);
    }
    return false;
  }

  boolean isReadyToSendGet(Response response, ServerClass server) {
    String readyToSend = "ready_to_send_" + server.toString().toLowerCase();
    if ((response != null) && response.hasEntity()) {
      String content = response.readEntity(String.class);
      try {
        return Stream.of(new ObjectMapper()
            .readValue(content, ResponseBodyObj[].class))
            .filter(responseObj -> readyToSend.equals(responseObj.name))
            .findFirst()
            .map(responseObj -> "1".equals(responseObj.value))
            .orElse(false);
      } catch (IOException ioe) {
        LOGGER.warn("Parsing CleverReach response to JSON failed. Content [{}]", content, ioe);
      }
    } else {
      LOGGER.warn("Mailing update failed with response [{}]", response);
    }
    return false;
  }

  Response sendRequest(String path, Object data, String authHeader, SubmitMethod method,
      String baseUrl) throws CleverReachRequestFailedException {
    AtomicReference<WebTarget> target = new AtomicReference(clientFactory.newClient().target(
        baseUrl).path(path));
    addGetParameters(data, target, method);
    Builder request = target.get().request().header("Authorization", authHeader);
    try {
      switch (method) {
        case GET:
          return request.get();
        case PUT:
          return request.put(getRequestDataEntity(data));
        case DELETE:
          return request.delete();
        default: // Default to SubmitMethod.POST
          return request.post(getRequestDataEntity(data));
      }
    } catch (ProcessingException pe) {
      LOGGER.error("[{}] call to CleverReach failed.", method, pe);
      throw new CleverReachRequestFailedException("[" + method + "] call to CleverReach failed.",
          RESPONSE_NO_BODY_LOGGING_MESSAGE, pe);
    }
  }

  String runDebugRequest(String path) throws IOException {
    Response response = sendRestRequest(path, new MultivaluedHashMap<String, String>(),
        SubmitMethod.GET);
    if ((response != null) && (response.getStatus() == 200) && response.hasEntity()) {
      return response.readEntity(String.class);
    }
    return "Response status [" + ((response != null) ? response.getStatus() : "?")
        + "]. See log for details.";
  }

  Entity<?> getRequestDataEntity(Object data) {
    if (data instanceof MultivaluedMap) {
      return Entity.form(getMultivalueMapFromOjb(data));
    } else { // POJO -> build JSON
      ObjectMapper mapper = new ObjectMapper();
      try {
        String dataJson = mapper.writeValueAsString(data);
        LOGGER.trace("JSON for request: [{}]", dataJson);
        return Entity.json(dataJson);
      } catch (IOException ioe) {
        LOGGER.error("Exception serializing data to json. Data [{}]", data, ioe);
      }
    }
    return Entity.text("");
  }

  void addGetParameters(Object data, AtomicReference<WebTarget> target, SubmitMethod method) {
    if ((method == SubmitMethod.GET) && (data instanceof MultivaluedMap)) {
      getMultivalueMapFromOjb(data).entrySet().stream().forEach(entry -> {
        target.set(target.get().queryParam(entry.getKey(), (entry.getValue().size() == 1)
            ? entry.getValue().get(0)
            : entry.getValue().toArray()));
        LOGGER.trace("addGetParameter: [{}]=[{}]", entry.getKey(), entry.getValue());
      });
    }
  }

  @SuppressWarnings("unchecked")
  MultivaluedMap<String, String> getMultivalueMapFromOjb(Object data) {
    return (MultivaluedMap<String, String>) data;
  }

  void traceLogLoginResponse(Response response, String content) {
    if (LOGGER.isTraceEnabled()) {
      MultivaluedMap<String, Object> headers = response.getHeaders();
      LOGGER.trace("Headers:");
      for (String header : headers.keySet()) {
        LOGGER.trace("header [{}] = [{}]", header, headers.get(header));
      }
      Map<String, NewCookie> cookies = response.getCookies();
      LOGGER.trace("Cookies:");
      for (String cookie : cookies.keySet()) {
        LOGGER.trace("cookie [{}] = [{}]", cookie, cookies.get(cookie));
      }
      LOGGER.trace("Content [{}]", content.replaceAll("^(.*\"access_token\":\")[^\"]*(.*)$",
          "$1********$2"));
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class ResponseBodyObj {

    public String name;
    public String value;

    @JsonCreator
    public ResponseBodyObj(@JsonProperty("name") String name,
        @JsonProperty("value") String value) {
      this.name = name;
      this.value = value;
    }
  }

  class Value {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE, include = As.WRAPPER_OBJECT)
    public String value;

    public Value(String value) {
      this.value = value;
    }
  }
}
