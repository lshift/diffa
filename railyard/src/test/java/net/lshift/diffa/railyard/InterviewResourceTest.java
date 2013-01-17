package net.lshift.diffa.railyard;

import net.lshift.diffa.adapter.scanning.ScanAggregation;
import net.lshift.diffa.adapter.scanning.ScanConstraint;
import net.lshift.diffa.adapter.scanning.ScanRequest;
import net.lshift.diffa.adapter.scanning.ScanResultEntry;
import net.lshift.diffa.config.RangeCategoryDescriptor;
import net.lshift.diffa.interview.NoFurtherQuestions;
import net.lshift.diffa.interview.Question;
import net.lshift.diffa.interview.SimpleGroupedAnswer;
import net.lshift.diffa.scanning.PruningHandler;
import net.lshift.diffa.scanning.Scannable;
import net.lshift.diffa.system.Endpoint;
import net.lshift.diffa.system.SystemConfiguration;
import net.lshift.diffa.versioning.VersionStore;
import org.apache.commons.lang.RandomStringUtils;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import java.util.ArrayList;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InterviewResourceTest {

  @Mock private SystemConfiguration conf;
  @Mock private VersionStore store;
  @Mock private Scannable diffStore;

  private InterviewResource interview;

  private String space = RandomStringUtils.randomAlphabetic(10);
  private String endpointName = RandomStringUtils.randomAlphabetic(10);

  private Endpoint endpoint = new Endpoint();

  @Before
  public void setUp() {

    MockitoAnnotations.initMocks(this);
    interview = new InterviewResource(store, conf, diffStore);

    endpoint.addCategory("some_date", new RangeCategoryDescriptor("date", "2000-01-01", "2010-01-01"));
    when(conf.getEndpoint(space, endpointName)).thenReturn(endpoint);
  }

  @Test
  public void shouldCreateInitialQuestion() throws Exception {


    Iterable<Question> questions = interview.getNextQuestion(space, endpointName);

    int aggregations = 0;
    int constraints = 0;

    for (Question question : questions) {
      aggregations =+ question.getAggregations().size();
      constraints =+ question.getConstraints().size();
    }

    assertEquals(1, aggregations);
    assertEquals(1, constraints);

  }

  @Test
  public void shouldCreateNextQuestion() throws Exception {

    MultivaluedMap<String, String> map = new MultivaluedMapImpl<String, String>();
    HttpRequest request = mock(HttpRequest.class);

    UriInfo uriInfo = mock(UriInfo.class);

    when(uriInfo.getQueryParameters()).thenReturn(map);
    when(request.getUri()).thenReturn(uriInfo);

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        PruningHandler handler = (PruningHandler) invocation.getArguments()[3];

        handler.onPrune(new SimpleGroupedAnswer("foo", "bar"));
        handler.onCompletion();

        return null;
      }
    }).when(diffStore).scan(anySetOf(ScanConstraint.class), anySetOf(ScanAggregation.class), anyInt(), any(PruningHandler.class));

    when(store.continueInterview(
          anyLong(),
          anySetOf(ScanConstraint.class),
          anySetOf(ScanAggregation.class),
          any(Iterable.class))).
        thenReturn(new NoFurtherQuestions());

    Iterable<Question> question = interview.getNextQuestion(space, endpointName, request);

    assertTrue(question instanceof NoFurtherQuestions);
  }
}
