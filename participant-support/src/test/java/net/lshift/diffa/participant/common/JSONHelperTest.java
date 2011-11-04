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

import net.lshift.diffa.participant.correlation.ProcessingResponse;
import net.lshift.diffa.participant.scanning.ScanResultEntry;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the JSON serialisation support.
 */
public class JSONHelperTest {
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
  public void shouldRoundtripSingleEntityWithNoAttributes() throws Exception {
    ScanResultEntry entry = ScanResultEntry.forEntity("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC));
    String single = serialiseResult(Arrays.asList(entry));
    ScanResultEntry[] deserialised = deserialiseResult(single);

    assertEquals(1, deserialised.length);
    assertEquals(entry, deserialised[0]);
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
    ScanResultEntry[] deserialised = deserialiseResult(single);

    assertEquals(1, deserialised.length);
    assertEquals(entry, deserialised[0]);
  }

  @Test
  public void shouldRoundtripFormattedEntity() throws Exception {
    ScanResultEntry entry = ScanResultEntry.forEntity("id1", "v1", new DateTime(2011, 6, 5, 15, 3, 0, 0, DateTimeZone.UTC), generateAttributes("a1v1", "a2v2"));
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    JSONHelper.writeQueryResult(baos, Arrays.asList(entry));
    String formatted = new String(baos.toByteArray(), "UTF-8");
    ScanResultEntry[] deserialised = deserialiseResult(formatted);

    assertEquals(1, deserialised.length);
    assertEquals(entry, deserialised[0]);
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

  private static ScanResultEntry[] deserialiseResult(String s) throws Exception {
    ByteArrayInputStream bais = new ByteArrayInputStream(s.getBytes("UTF-8"));
    return JSONHelper.readQueryResult(new ByteArrayInputStream(s.getBytes("UTF-8")));
  }

  private static ProcessingResponse deserialiseResponse(String s) throws Exception {
    ByteArrayInputStream bais = new ByteArrayInputStream(s.getBytes("UTF-8"));
    return JSONHelper.readProcessingResponse(new ByteArrayInputStream(s.getBytes("UTF-8")));
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
