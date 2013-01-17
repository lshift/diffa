package net.lshift.diffa.railyard.plumbing;

import net.lshift.diffa.events.ChangeEvent;
import net.lshift.diffa.events.UpsertEvent;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;

import javax.xml.stream.XMLEventWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;

public class EventPipe<T> extends InputStream {

  private final PipedInputStream is;

  public EventPipe(Iterable<T> events, final EventWriter<T> eventWriter, ExecutorService executorService, JsonFactory factory) {
    final Iterator<T> iterator = events.iterator();
    final PipedOutputStream os = new PipedOutputStream();

    try {
      this.is = new PipedInputStream(os);

      final JsonGenerator generator = factory.createJsonGenerator(os);

      executorService.execute(new Runnable() {

        @Override
        public void run() {
          try {
            generator.writeStartArray();

            while (iterator.hasNext()) {
              T event = iterator.next();
              eventWriter.writeEvent(event, generator);
              generator.flush();
            }

            generator.writeEndArray();
            generator.flush();

            os.flush();
            os.close();

          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });

    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }

  @Override
  public int read() throws IOException {
    return is.read();
  }
}
