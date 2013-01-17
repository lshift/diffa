package net.lshift.diffa.railyard.plumbing;

import com.ning.http.client.*;
import net.lshift.diffa.interview.Question;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class QuestionHandler implements AsyncHandler<Response>, Iterable<Question> {

  private static Logger log = LoggerFactory.getLogger(QuestionHandler.class);

  private JsonFactory factory = new JsonFactory();
  private final LinkedBlockingQueue<Question> queue = new LinkedBlockingQueue<Question>();
  private CountDownLatch bodyAvailable = new CountDownLatch(1);
  private CountDownLatch complete = new CountDownLatch(1);

  @Override
  public void onThrowable(Throwable t) {
    log.error("Strange error", t);
  }

  @Override
  public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {

    JsonParser parser = factory.createJsonParser(bodyPart.getBodyPartBytes());
    JsonToken current;

    while ((current = parser.nextToken()) != JsonToken.END_ARRAY) {
      if (current == JsonToken.START_OBJECT) {
        Question question = parser.readValueAs(Question.class);
        queue.put(question);
        bodyAvailable.countDown();
      }
    }

    return STATE.CONTINUE;
  }

  @Override
  public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {

    if (responseStatus.getStatusCode() != 200) {
      log.error("Received HTTP " + responseStatus.getStatusCode() + " : " + responseStatus.getStatusText());
      return STATE.ABORT;
    }
    else {

      return STATE.CONTINUE;
    }
  }

  @Override
  public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
    return STATE.CONTINUE;
  }

  @Override
  public Response onCompleted() throws Exception {
    complete.countDown();
    return null;
  }

  @Override
  public Iterator<Question> iterator() {
    return new Iterator<Question>() {

      @Override
      public boolean hasNext() {

        try {
          bodyAvailable.await();

          boolean isEmpty = queue.isEmpty();

          if (isEmpty) {
            complete.await();
            return queue.isEmpty();
          }
          else {
            return true;
          }

        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }

      }

      @Override
      public Question next() {
        try {
          return queue.take();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public void remove() {}
    };
  }


}
