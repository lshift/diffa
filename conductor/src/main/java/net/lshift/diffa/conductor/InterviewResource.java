package net.lshift.diffa.conductor;

import com.google.inject.Inject;
import net.lshift.diffa.adapter.scanning.ScanAggregation;
import net.lshift.diffa.adapter.scanning.ScanConstraint;
import net.lshift.diffa.conductor.plumbing.InterviewResultHandler;
import net.lshift.diffa.interview.NoFurtherQuestions;
import net.lshift.diffa.interview.Question;
import net.lshift.diffa.railyard.RailYard;
import net.lshift.diffa.scanning.Scannable;
import net.lshift.diffa.sql.PartitionAwareDriver;
import net.lshift.diffa.sql.PartitionMetadata;
import org.jboss.resteasy.spi.BadRequestException;
import org.jboss.resteasy.spi.NotFoundException;
import org.joda.time.DateTime;
import org.jooq.SQLDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import javax.ws.rs.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static net.lshift.diffa.conductor.plumbing.ConfigurationBuilder.*;

@Path("/{space}/interview")
public class InterviewResource {

  static Logger log = LoggerFactory.getLogger(InterviewResource.class);

  @Inject
  private RailYard railyard;

  private Map<String, Scannable> drivers = new ConcurrentHashMap<String, Scannable>();
  private Map<Long, InterviewState> interviews = new ConcurrentHashMap<Long, InterviewState>();

  private ExecutorService executor = Executors.newCachedThreadPool();

  @POST
  @Path("/{endpoint}/config")
  @Consumes("application/json")
  public void registerDriver(@PathParam("space") String space, @PathParam("endpoint") String endpoint, DriverConfiguration config) {

    DataSource ds = buildDataSource(config);
    PartitionMetadata metadata = buildMetaData(config);

    PartitionAwareDriver driver = new PartitionAwareDriver(ds, metadata, SQLDialect.valueOf(config.getDialect()));
    registerDriver(space, endpoint, driver);
  }

  @POST
  @Path("/{endpoint}")
  @Produces("text/plain")
  public Long begin(@PathParam("space") String space, @PathParam("endpoint") String endpoint) {

    Scannable driver = getDriver(space, endpoint);

    if (driver == null) {
      throw new BadRequestException("No driver registered for " + space + " / " + endpoint);
    }

    Long id  = System.currentTimeMillis(); // TODO wire in snowflake

    log.info("Kicking off interview ({}) for {} endpoint in space {}", new Object[]{id, endpoint, space});

    Interview interview = new Interview(id, railyard, driver, space, endpoint);
    executor.execute(interview);

    interviews.put(id, new InterviewState(id, "STARTED", new DateTime().toString()));

    return id;

  }

  @GET
  @Path("/{id}/progress")
  @Produces("application/json")
  public InterviewState getProgress(@PathParam("id") Long id) {
    if (interviews.containsKey(id)) {
      return interviews.get(id);
    }
    else {
      throw new NotFoundException("id = " + id);
    }

  }

  private Scannable getDriver(String space, String endpoint) {
    return drivers.get(space + "." + endpoint);
  }

  private void registerDriver(String space, String endpoint, Scannable driver) {
    drivers.put(space + "." + endpoint, driver);
  }



  private class Interview implements Runnable {

    private final Long id;
    private final RailYard railyard;
    private final Scannable scannable;
    private final String space, endpoint;

    private Interview(Long id, RailYard railyard, Scannable scannable, String space, String endpoint) {
      this.id = id;
      this.railyard = railyard;
      this.scannable = scannable;
      this.space = space;
      this.endpoint = endpoint;
    }

    @Override
    public void run() {

      Iterable<Question> questions = railyard.getNextQuestions(space, endpoint);

      progressInterview(questions);

      while ( !(questions instanceof NoFurtherQuestions) ) {



      }

      InterviewState state = interviews.get(id);
      state.setEnd(new DateTime().toString());
      state.setState("FINISHED");
      interviews.put(id, state);

    }

    private void progressInterview(Iterable<Question> questions) {
      for (Question question : questions) {

        Set<ScanConstraint> constraints = question.getConstraints();
        Set<ScanAggregation> aggregations = question.getAggregations();
        int maxSliceSize = question.getMaxSliceSize();

        InterviewResultHandler handler = new InterviewResultHandler();

        scannable.scan(constraints, aggregations, maxSliceSize, handler);

        Iterable<Question> nextQuestions = railyard.getNextQuestions(space, endpoint, question, handler);
        progressInterview(nextQuestions);
      }
    }
  }
}
