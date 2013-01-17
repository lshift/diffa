package net.lshift.diffa.railyard;

import com.google.inject.Inject;
import net.lshift.diffa.adapter.scanning.*;
import net.lshift.diffa.config.CategoryDescriptor;
import net.lshift.diffa.interview.NoFurtherQuestions;
import net.lshift.diffa.interview.Question;
import net.lshift.diffa.railyard.questions.QuestionBuilder;
import net.lshift.diffa.railyard.plumbing.RestEasyRequestWrapper;
import net.lshift.diffa.scanning.Scannable;
import net.lshift.diffa.scanning.plumbing.BufferedPruningHandler;
import net.lshift.diffa.system.Endpoint;
import net.lshift.diffa.system.SystemConfiguration;
import net.lshift.diffa.versioning.VersionStore;
import org.jboss.resteasy.spi.HttpRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Path("/{space}/interview")
public class InterviewResource {

  private VersionStore versionStore;
  private SystemConfiguration systemConfig;
  private Scannable diffsStore;

  @Inject
  public InterviewResource(VersionStore versionStore, SystemConfiguration systemConfig, Scannable scannable) {
    this.versionStore = versionStore;
    this.systemConfig = systemConfig;
    this.diffsStore = scannable;
  }

  @GET
  @Path("/{endpoint}")
  @Produces("application/json")
  public Iterable<Question> getNextQuestion(@PathParam("space") String space, @PathParam("endpoint") String endpointName) {

    List<Question> questions = new ArrayList<Question>(1);

    Endpoint endpoint = systemConfig.getEndpoint(space, endpointName);
    Map<String,CategoryDescriptor> categories = endpoint.getCategories();

    Question initialQuestion = QuestionBuilder.buildInitialQuestion(categories);
    questions.add(initialQuestion);

    return questions;
  }

  @POST
  @Path("/{endpoint}")
  @Produces("application/json")
  public Iterable<Question> getNextQuestion(@PathParam("space") String space, @PathParam("endpoint") String endpointName,
                                            @Context final HttpRequest request) throws IOException {

    Endpoint endpoint = systemConfig.getEndpoint(space, endpointName);

    HttpRequestParameters parameters = new RestEasyRequestWrapper(request);
    SliceSizeParser sliceSizeParser = new SliceSizeParser(parameters);

    AggregationBuilder aggregationBuilder = new AggregationBuilder(parameters);
    ConstraintsBuilder constraintsBuilder = new ConstraintsBuilder(parameters);

    Set<ScanConstraint> constraints = constraintsBuilder.toSet();
    Set<ScanAggregation> aggregates = aggregationBuilder.toSet();
    int maxSliceSize = sliceSizeParser.getMaxSliceSize();

    BufferedPruningHandler handler = new BufferedPruningHandler();
    diffsStore.scan(constraints, aggregates, maxSliceSize, handler);

    return versionStore.continueInterview(endpoint.getId(), constraints, aggregates, handler.getAnswers());
  }
}
