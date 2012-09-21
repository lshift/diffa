/**
 * Copyright (C) 2010-2011 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.lshift.diffa.participant.common;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import net.lshift.diffa.participant.changes.ChangeEvent;
import net.lshift.diffa.participant.correlation.ProcessingResponse;
import net.lshift.diffa.participant.scanning.ScanResultEntry;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.easymock.EasyMock.*;

/**
 * Tests for the JSON serialisation support.
 */
public class JSONHelperTest {
  private class CountingEventAppender extends AppenderBase<ILoggingEvent> {
    private int eventCount = 0;
    
    public void resetCount() {
      eventCount = 0;
    }
    
    public int getEventCount() {
      return eventCount;
    }

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
      eventCount++;
    }
  }

  private CountingEventAppender logAppender = new CountingEventAppender();

  {
    LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
    logAppender.setContext(ctx);
    logAppender.start();
    Logger jsonHelperLogger = (Logger) LoggerFactory.getLogger(JSONHelper.class);
    jsonHelperLogger.addAppender(logAppender);
  }

  private final static ScanEntityValidator nullValidator = new ScanEntityValidator () {
    public void process(ScanResultEntry e) {}
    public void process(ChangeEvent e)  {}
  };

  @Before
  public void setup() {
    logAppender.resetCount();
  }

  @Test
  public void shouldSerialiseEmptyList() throws Exception {
    String emptyRes = serialiseResult(new ArrayList<ScanResultEntry>());
    assertJSONEquals("[]", emptyRes);
  }

  @Test
  public void shouldSerialiseSingleEntityWithNoAttributes() throws Exception {
    String single = serialiseResult(Arrays.asList(
      ScanResultEntry.forEntity("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC))));
    assertJSONEquals("[{\"id\":\"id1\",\"version\":\"v1\",\"lastUpdated\":\"2011-06-05T15:03:00.000Z\"}]", single);
  }

  @Test
  public void shouldRoundTripByteArrayRepresentation() throws Exception {
    ChangeEvent event = ChangeEvent.forChange("foo", "bar", new DateTime(1977,5,6,4,5,8,0, DateTimeZone.UTC));
    byte[] buffer = JSONHelper.writeChangeEvent(event);
    ByteArrayInputStream is = new ByteArrayInputStream(buffer);
    ChangeEvent[] events = JSONHelper.readChangeEvents(is, nullValidator);
    assertEquals(event, events[0]);
  }
  
  @Test
  public void shouldRoundtripSingleEntityWithNoAttributes() throws Exception {
    ScanResultEntry entry = ScanResultEntry.forEntity("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC));
    String single = serialiseResult(Arrays.asList(entry));
    List<ScanResultEntry> deserialised = deserialiseResult(single);

    assertEquals(1, deserialised.size());
    assertEquals(entry, deserialised.get(0));
  }

  @Test
  public void shouldSerialiseSingleEntityWithAttributes() throws Exception {
    String single = serialiseResult(Arrays.asList(
      ScanResultEntry.forEntity("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC),
        generateAttributes("a1v1", "a2v2"))));
    assertJSONEquals(
      "[{\"id\":\"id1\",\"attributes\":{\"a1\":\"a1v1\",\"a2\":\"a2v2\"},\"version\":\"v1\",\"lastUpdated\":\"2011-06-05T15:03:00.000Z\"}]",
      single);
  }

  @Test
  public void shouldRoundtripSingleEntityWithAttributes() throws Exception {
    ScanResultEntry entry = ScanResultEntry.forEntity("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC), generateAttributes("a1v1", "a2v2"));
    String single = serialiseResult(Arrays.asList(entry));
    List<ScanResultEntry> deserialised = deserialiseResult(single);

    assertEquals(1, deserialised.size());
    assertEquals(entry, deserialised.get(0));
  }

  @Test
  public void shouldRoundtripFormattedEntity() throws Exception {
    ScanResultEntry entry = ScanResultEntry.forEntity("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC), generateAttributes("a1v1", "a2v2"));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    JSONHelper.writeQueryResult(baos, Arrays.asList(entry));
    String formatted = new String(baos.toByteArray(), "UTF-8");
    List<ScanResultEntry> deserialised = deserialiseResult(formatted);

    assertEquals(1, deserialised.size());
    assertEquals(entry, deserialised.get(0));
  }

  @Test
  public void shouldRoundtripProcessingResponseWithoutAttributes() throws Exception {
    ProcessingResponse resp = new ProcessingResponse("id1", "uv1", "dv1");
    String respJ = serialiseResponse(resp);
    ProcessingResponse deserialized = deserialiseResponse(respJ);

    assertEquals(resp, deserialized);
  }

  @Test
  public void shouldRoundtripProcessingResponseWithAttributes() throws Exception {
    ProcessingResponse resp = new ProcessingResponse("id1", generateAttributes("a1v1", "a1v2"), "uv1", "dv1");
    String respJ = serialiseResponse(resp);
    ProcessingResponse deserialized = deserialiseResponse(respJ);

    assertEquals(resp, deserialized);
  }

  @Test
  public void shouldSerialiseEmptyChangeList() throws Exception {
    String emptyRes = serialiseEvents(new ArrayList<ChangeEvent>());
    assertJSONEquals("[]", emptyRes);
  }

  @Test
  public void shouldSerialiseSingleEventWithNoAttributes() throws Exception {
    String single = serialiseEvent(
      ChangeEvent.forChange("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC)));
    assertJSONEquals("{\"id\":\"id1\",\"version\":\"v1\",\"lastUpdated\":\"2011-06-05T15:03:00.000Z\"}", single);
  }

  @Test
  public void shouldRoundtripSingleEventWithNoAttributes() throws Exception {
    ChangeEvent event =
      ChangeEvent.forChange("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC));
    String single = serialiseEvent(event);
    ChangeEvent[] deserialised = deserialiseEvents(single);

    assertEquals(1, deserialised.length);
    assertEquals(event, deserialised[0]);
  }


  @Test
  public void shouldPassChangeEventsToProcessorWhenSerializedAsArray() throws Exception {
    //Given
    ScanEntityValidator proc = createMock(ScanEntityValidator.class);
    Map<String, String> attributes= new HashMap<String, String>();
    attributes.put("a1", "v1");

    ChangeEvent expected = ChangeEvent.forChange("id1", "v1",
            new DateTime(2011, 06, 05, 15, 03, 0, DateTimeZone.UTC), attributes);

    proc.process(expected); replay(proc);
    String changeEvents = serialiseEvents(Arrays.asList(expected));
    InputStream in = new ByteArrayInputStream(changeEvents.getBytes());

    JSONHelper.readChangeEvents(in, proc);
    verify(proc);
  }

  @Test
  public void shouldPassChangeEventsToProcessorWhenSerializedAsObject() throws Exception {
    //Given
    ScanEntityValidator proc = createMock(ScanEntityValidator.class);
    Map<String, String> attributes= new HashMap<String, String>();
    attributes.put("dummy", "b52");

    ChangeEvent expected = ChangeEvent.forChange("id42", "v2",
            new DateTime(2012, 06, 07, 15, 03, 0, DateTimeZone.UTC), attributes);

    proc.process(expected); replay(proc);
    String changeEvents = serialiseEvent(expected);
    InputStream in = new ByteArrayInputStream(changeEvents.getBytes());

    JSONHelper.readChangeEvents(in, proc);
    verify(proc);
  }


  @Test
  public void shouldSerialiseEventListWithAttributes() throws Exception {
    String list = serialiseEvents(Arrays.asList(
      ChangeEvent.forChange("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC), generateAttributes("a1v1", "a2v2")),
      ChangeEvent.forTriggeredChange("id2", "v2", "uv1", new DateTime(2012, 7, 6, 16, 4, 0, 0, DateTimeZone.UTC), generateAttributes("a1v1", "a2v2"))
    ));
    assertJSONEquals(
      "[" +
        "{\"id\":\"id1\",\"attributes\":{\"a1\":\"a1v1\",\"a2\":\"a2v2\"},\"version\":\"v1\",\"lastUpdated\":\"2011-06-05T15:03:00.000Z\"}," +
        "{\"id\":\"id2\",\"attributes\":{\"a1\":\"a1v1\",\"a2\":\"a2v2\"},\"version\":\"v2\",\"lastUpdated\":\"2012-07-06T16:04:00.000Z\",\"parentVersion\":\"uv1\"}" +
      "]",
      list);
  }

  @Test
  public void shouldRoundtripEventListWithAttributes() throws Exception {
    ChangeEvent e1 = ChangeEvent.forChange("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC), generateAttributes("a1v1", "a2v2"));
    ChangeEvent e2 = ChangeEvent.forTriggeredChange("id2", "v2", "uv2", new DateTime(2012, 7, 6, 16, 4, 0, 0, DateTimeZone.UTC), generateAttributes("a1v1", "a2v2"));

    String list = serialiseEvents(Arrays.asList(e1, e2));
    ChangeEvent[] deserialised = deserialiseEvents(list);

    assertEquals(2, deserialised.length);
    assertEquals(e1, deserialised[0]);
    assertEquals(e2, deserialised[1]);
  }

  @Test
  public void shouldLogScanResultEntryCountForEntityQuery() throws Exception {
    //Given
    String scanResult = "[" +
        makeJsonEntityString("id1", ",\"attributes\":{\"a1\":\"v1\"}", "v1", "2011-06-05T15:03:00.000Z") + "," +
        makeJsonEntityString("id2", ",\"attributes\":{\"a1\":\"v1\"}", "v2", "2011-06-05T15:03:00.000Z") +
        "]";
    InputStream in = new ByteArrayInputStream(scanResult.getBytes());

    // When
    JSONHelper.readQueryResult(in, nullValidator);

    // Then
    assertEquals("Should log exactly one event", 1, logAppender.getEventCount());
  }


  @Test
  public void shouldPassScannedEntityToProcessor() throws Exception {
    //Given
    ScanEntityValidator proc = createMock(ScanEntityValidator.class);
    Map<String, String> attributes= new HashMap<String, String>();
    attributes.put("a1", "v1");

    ScanResultEntry expected = ScanResultEntry.forEntity("id1", "v1",
            new DateTime(2011, 06, 05, 15, 03, 0, DateTimeZone.UTC), attributes);

    proc.process(expected); replay(proc);
    String scanResult = "[" +
            makeJsonEntityString("id1", ",\"attributes\":{\"a1\":\"v1\"}", "v1", "2011-06-05T15:03:00.000Z") + "]";
    InputStream in = new ByteArrayInputStream(scanResult.getBytes());

    // When
    JSONHelper.readQueryResult(in, proc);


    // Then
    verify(proc);
  }


  @Test
  public void shouldLogScanResultEntryCountForAggregateQuery() throws Exception {
    // Given
    String scanResult = "[" + makeJsonAggregateString("\"attributes\":{\"a1\":\"v1\"}", "v1") + "]";
    InputStream in = new ByteArrayInputStream(scanResult.getBytes());

    // When
    JSONHelper.readQueryResult(in, nullValidator);

    // Then
    assertEquals("Should log exactly one event", 1, logAppender.getEventCount());
  }

  private static String makeJsonAggregateString(String attributes, String version) {
    return makeJsonEntityString(null, attributes, version, null);
  }

  private static String makeJsonEntityString(String id, String attributes, String version, String lastUpdated) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("{");
    if (id != null)
      stringBuilder.append("\"id\":\"" + id + "\"");
    stringBuilder.append(attributes);
    stringBuilder.append(",\"version\":\"" + version + "\"");
    if (lastUpdated != null)
      stringBuilder.append(",\"lastUpdated\":\"" + lastUpdated + "\"");
    stringBuilder.append("}");
    return stringBuilder.toString();
  }

  private static String serialiseResult(Iterable<ScanResultEntry> entries) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    JSONHelper.writeQueryResult(baos, entries);

    return new String(baos.toByteArray(), "UTF-8");
  }

  private static String serialiseResponse(ProcessingResponse response) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    JSONHelper.writeProcessingResponse(baos, response);

    return new String(baos.toByteArray(), "UTF-8");
  }

  private static String serialiseEvent(ChangeEvent event) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    JSONHelper.writeChangeEvent(baos, event);

    return new String(baos.toByteArray(), "UTF-8");
  }
  private static String serialiseEvents(Iterable<ChangeEvent> events) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    JSONHelper.writeChangeEvents(baos, events);

    return new String(baos.toByteArray(), "UTF-8");
  }

  private static List<ScanResultEntry> deserialiseResult(String s) throws Exception {
    ByteArrayInputStream bais = new ByteArrayInputStream(s.getBytes("UTF-8"));
    return JSONHelper.readQueryResult(new ByteArrayInputStream(s.getBytes("UTF-8")),
            nullValidator);
  }

  private static ProcessingResponse deserialiseResponse(String s) throws Exception {
    ByteArrayInputStream bais = new ByteArrayInputStream(s.getBytes("UTF-8"));
    return JSONHelper.readProcessingResponse(new ByteArrayInputStream(s.getBytes("UTF-8")));
  }

  private static ChangeEvent[] deserialiseEvents(String s) throws Exception {
    ByteArrayInputStream bais = new ByteArrayInputStream(s.getBytes("UTF-8"));
    return JSONHelper.readChangeEvents(new ByteArrayInputStream(s.getBytes("UTF-8")), nullValidator);
  }


  private static Map<String, String> generateAttributes(String a1, String a2) {
    Map<String, String> result = new HashMap<String, String>();
    result.put("a1", a1);
    result.put("a2", a2);
    return result;
  }

  private static void assertJSONEquals(String expected, String actual) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode expectedTree = mapper.readTree(expected);
    JsonNode actualTree = mapper.readTree(actual);

    assertEquals(expectedTree, actualTree);
  }
}
