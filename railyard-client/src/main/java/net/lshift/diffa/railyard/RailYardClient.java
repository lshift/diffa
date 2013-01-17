package net.lshift.diffa.railyard;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.ning.http.client.*;
import com.ning.http.client.generators.InputStreamBodyGenerator;
import net.lshift.diffa.events.ChangeEvent;
import net.lshift.diffa.interview.Answer;
import net.lshift.diffa.interview.Question;
import net.lshift.diffa.plumbing.BufferedJsonBodyConsumer;
import net.lshift.diffa.railyard.plumbing.*;
import net.lshift.diffa.scanning.http.ScanRequestEncoder;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.map.ObjectMapper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RailYardClient implements RailYard {

  private JsonFactory factory = new JsonFactory();
  private ExecutorService executorService = Executors.newCachedThreadPool();
  //private AsyncHttpClient client = new AsyncHttpClient();
  private String baseUrl;

  @Inject
  public RailYardClient(@Named("railYardUrl") String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public void postChanges(String space, String endpoint, Iterable<ChangeEvent> events) {

    if (events == null || !events.iterator().hasNext() ) {
      throw new RailYardException("Client must supply at least one change event");
    }

    EventPipe eventPipe = new ChangeEventPipe(events, executorService, factory);
    InputStreamBodyGenerator encoder = new InputStreamBodyGenerator(eventPipe);

    final String url = baseUrl + String.format("/%s/changes/%s", space, endpoint);

    Request request =
        new RequestBuilder("POST").
          setUrl(url).
          setBody(encoder).
          build();

    AsyncHttpClient client = new AsyncHttpClient();

    try {

      Response response = client.prepareRequest(request).execute().get();
      ResponseHelper.verifyResponse(response, 204);

    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      client.closeAsynchronously();
    }


  }

  @Override
  public Iterable<Question> getNextQuestions(String space, String endpoint) {

    final String url = baseUrl + String.format("/%s/interview/%s", space, endpoint);

    QuestionHandler handler = new QuestionHandler();
    AsyncHttpClient client = new AsyncHttpClient();

    try {

      client.prepareGet(url).execute(handler);

    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      client.closeAsynchronously();
    }

    return handler;
  }

  @Override
  public Iterable<Question> getNextQuestions(String space, String endpoint, Question lastQuestion, Iterable<Answer> answers) {

    final String url = baseUrl + String.format("/%s/interview/%s", space, endpoint);

    FluentStringsMap queryParameters = ScanRequestEncoder.packRequest(lastQuestion.getConstraints(), lastQuestion.getAggregations(), lastQuestion.getMaxSliceSize());

    EventPipe eventPipe = new AnswerEventPipe(answers, executorService, factory);
    InputStreamBodyGenerator encoder = new InputStreamBodyGenerator(eventPipe);

    Request request =
        new RequestBuilder("POST").
            setUrl(url).
            setBody(encoder).
            setQueryParameters(queryParameters).
            build();

    QuestionHandler handler = new QuestionHandler();
    AsyncHttpClient client = new AsyncHttpClient();

    try {

      client.prepareRequest(request).execute(handler);
      /*
      verifyResponse(response, 200);

      byte[] payload = response.getResponseBodyAsBytes();
      question = mapper.readValue(payload, Question.class);
      */

    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      client.closeAsynchronously();
    }

    return handler;
  }


}
