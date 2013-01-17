package net.lshift.diffa.conductor;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.ning.http.client.*;
import com.ning.http.client.generators.ByteArrayBodyGenerator;
import net.lshift.diffa.plumbing.BufferedJsonBodyConsumer;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ConductorClient implements Conductor {

  static Logger log = LoggerFactory.getLogger(ConductorClient.class);

  private ObjectMapper mapper = new ObjectMapper();
  private AsyncHttpClient client = new AsyncHttpClient();
  private String baseUrl;

  @Inject
  public ConductorClient(@Named("conductorUrl") String baseUrl) {
    this.baseUrl = baseUrl;
  }

  @Override
  public void registerDriver(String space, String endpoint, DriverConfiguration configuration) {

    BodyGenerator body = getSerializedPayload(configuration);
    String url = baseUrl + String.format("/%s/interview/%s/config", space, endpoint);

    Request request
        = new RequestBuilder("POST").
        setUrl(url).
        setBody(body).
        setHeader("Content-Type", "application/json").
        build();

    try {

      Response response = client.prepareRequest(request).execute().get();
      verifyResponse(response, 204);

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Long begin(String space, String endpoint) {

    String url = baseUrl + String.format("/%s/interview/%s", space, endpoint);

    Request request
        = new RequestBuilder("POST").
          setUrl(url).
          build();

    try {

      Response response = client.prepareRequest(request).execute().get();
      verifyResponse(response, 200);
      return Long.parseLong(response.getResponseBody());

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public InterviewState getProgress(String space, Long id) {
    InterviewState state = null;

    final String url = baseUrl + String.format("/%s/interview/%s/progress", space, id);

    SimpleAsyncHttpClient httpClient = new SimpleAsyncHttpClient.Builder()
        .setUrl(url)
        .build();

    BufferedJsonBodyConsumer consumer = new BufferedJsonBodyConsumer();

    try {

      Response response = httpClient.get(consumer).get();
      verifyResponse(response, 200);
      state = consumer.getValue(InterviewState.class);

    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return state;
  }

  private BodyGenerator getSerializedPayload(Object o) {

    byte[] payload = null;

    try {
      payload = mapper.writeValueAsBytes(o);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return new ByteArrayBodyGenerator(payload);
  }

  private void verifyResponse(Response response, int code) {
    if (response.getStatusCode() != code) {
      switch (response.getStatusCode()) {
        default: {
          log.error("Expected an HTTP " + code + ", but got " + response.getStatusCode() + "; " + response.getStatusText());
          throw new ConductorException("HTTP " + response.getStatusCode() + " : " + response.getStatusText());
        }
      }
    }
  }
}
