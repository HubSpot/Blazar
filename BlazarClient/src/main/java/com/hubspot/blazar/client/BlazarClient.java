package com.hubspot.blazar.client;

import com.google.common.base.Optional;
import com.hubspot.blazar.base.ModuleBuild;
import com.hubspot.horizon.HttpClient;
import com.hubspot.horizon.HttpRequest;
import com.hubspot.horizon.HttpRequest.Method;
import com.hubspot.horizon.HttpResponse;

public class BlazarClient {
  private static final String MODULE_BUILD_PATH = "/modules/builds";
  private static final String GET_MODULE_BUILD_PATH = MODULE_BUILD_PATH + "/%s";
  private static final String START_MODULE_BUILD_PATH = MODULE_BUILD_PATH + "/%s/start";
  private static final String COMPLETE_MODULE_BUILD_SUCCESS_PATH = MODULE_BUILD_PATH + "/%s/success";
  private static final String COMPLETE_MODULE_BUILD_FAILURE_PATH = MODULE_BUILD_PATH + "/%s/failure";

  private final HttpClient httpClient;
  private final String baseUrl;

  // Create via BlazarClientConfig
  BlazarClient(HttpClient httpClient, String baseUrl) {
    this.httpClient = httpClient;
    this.baseUrl = baseUrl;
  }

  public Optional<ModuleBuild> getModuleBuild(long moduleBuildId) {
    String url = String.format(baseUrl + GET_MODULE_BUILD_PATH, moduleBuildId);
    HttpRequest request = HttpRequest.newBuilder().setMethod(Method.GET).setUrl(url).build();

    HttpResponse response = httpClient.execute(request);
    if (response.getStatusCode() == 404) {
      return Optional.absent();
    } else if (response.getStatusCode() == 200) {
      return Optional.of(response.getAs(ModuleBuild.class));
    } else {
      throw toException(response);
    }
  }

  public void startModuleBuild(long moduleBuildId, String taskId) {
    String url = String.format(baseUrl + START_MODULE_BUILD_PATH, moduleBuildId);
    HttpRequest request = HttpRequest.newBuilder().setMethod(Method.PUT).setUrl(url).build();

    HttpResponse response = httpClient.execute(request);
    if (response.getStatusCode() != 200) {
      throw toException(response);
    }
  }

  public void completeModuleBuildSuccess(long moduleBuildId) {
    String url = String.format(baseUrl + COMPLETE_MODULE_BUILD_SUCCESS_PATH, moduleBuildId);
    HttpRequest request = HttpRequest.newBuilder().setMethod(Method.PUT).setUrl(url).build();

    HttpResponse response = httpClient.execute(request);
    if (response.getStatusCode() != 200) {
      throw toException(response);
    }
  }

  public void completeModuleBuildFailure(long moduleBuildId) {
    String url = String.format(baseUrl + COMPLETE_MODULE_BUILD_FAILURE_PATH, moduleBuildId);
    HttpRequest request = HttpRequest.newBuilder().setMethod(Method.PUT).setUrl(url).build();

    HttpResponse response = httpClient.execute(request);
    if (response.getStatusCode() != 200) {
      throw toException(response);
    }
  }

  private BlazarClientException toException(HttpResponse response) {
    String messageFormat = "Error hitting URL %s, status code: %s, response: %s";
    String message = String.format(messageFormat, response.getRequest().getUrl(), response.getStatusCode(), response.getAsString());
    throw new BlazarClientException(message);
  }
}