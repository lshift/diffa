package net.lshift.diffa.plumbing;

import com.ning.http.client.BodyConsumer;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class BufferedJsonBodyConsumer implements BodyConsumer {

  private AtomicBoolean complete = new AtomicBoolean(false);
  private ObjectMapper mapper = new ObjectMapper();
  private final ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

  public <T> T getValue(Class<T> type) {
    T value;

    long timeToWait = 1L;
    while (!complete.get() && timeToWait < 30000L) {
      try {
        Thread.sleep(timeToWait *= 2);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    byte[] dest = new byte[buffer.remaining()];
    buffer.get(dest, 0, dest.length);

    try {
      value = mapper.readValue(dest, type);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return value;
  }

  /**
   * {@inheritDoc}
   */
    /* @Override */
  public void consume(ByteBuffer byteBuffer) throws IOException {
    buffer.put(byteBuffer);
  }

  /**
   * {@inheritDoc}
   */
    /* @Override */
  public void close() throws IOException {
    buffer.flip();
    complete.set(true);
  }
}
