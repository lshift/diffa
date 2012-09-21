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

import net.lshift.diffa.participant.changes.ChangeEvent;
import net.lshift.diffa.participant.correlation.ProcessingResponse;
import net.lshift.diffa.participant.scanning.ScanResultEntry;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper for serializing scanning results in JSON.
 */
public class JSONHelper {
  private static ObjectMapper mapper = new ObjectMapper();
  private static ObjectMapper prettyMapper = new ObjectMapper();
  private static JsonFactory jsonFactory = new JsonFactory();
  private static Logger log = LoggerFactory.getLogger(JSONHelper.class);
  static {
    mapper.getSerializationConfig().set(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS, false);
    prettyMapper.getSerializationConfig().set(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS, false);
    prettyMapper.getSerializationConfig().set(SerializationConfig.Feature.INDENT_OUTPUT, true);
  }

  public static void writeQueryResult(OutputStream responseStream, Iterable<ScanResultEntry> entries)
      throws IOException {
    try {
      mapper.writeValue(responseStream, entries);
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Failed to serialize result to JSON", ex);
    }
  }

  public static void formatQueryResult(OutputStream responseStream, Iterable<ScanResultEntry> entries)
      throws IOException {
    try {
      prettyMapper.writeValue(responseStream, entries);
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Failed to serialize result to JSON", ex);
    }
  }

  public static List<ScanResultEntry> readQueryResult(InputStream stream, ScanEntityValidator validator)
      throws IOException {
    try {
      List<ScanResultEntry> scanResultEntries = new ArrayList<ScanResultEntry>();
      JsonParser parser = jsonFactory.createJsonParser(stream);
      if (parser.nextToken() != JsonToken.START_ARRAY) {
        throw new Exception("Expected '[' (JSON array start)");
      }

      while (parser.nextToken() != JsonToken.END_ARRAY) {
          ScanResultEntry entry = mapper.readValue(parser, ScanResultEntry.class);
          validator.process(entry);
          scanResultEntries.add(entry);

      }
      log.info("ScanResultEntry readQueryResult [count = " + scanResultEntries.size() + "]");
      return scanResultEntries;
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Failed to deserialize result from JSON: %s".format(ex.getMessage()), ex);
    }
  }

  public static void writeProcessingResponse(OutputStream responseStream, ProcessingResponse response)
      throws IOException {
    try {
      mapper.writeValue(responseStream, response);
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Failed to serialize result to JSON", ex);
    }
  }

  public static ProcessingResponse readProcessingResponse(InputStream stream)
      throws IOException {
    try {
      return mapper.readValue(stream, ProcessingResponse.class);
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Failed to deserialize result from JSON", ex);
    }
  }

  public static void writeChangeEvent(OutputStream responseStream, ChangeEvent event)
      throws IOException {
    try {
      mapper.writeValue(responseStream, event);
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Failed to serialize event to JSON", ex);
    }
  }

  public static byte[] writeChangeEvent(ChangeEvent event)
        throws IOException {
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeChangeEvent(baos, event);
        return baos.toByteArray();
      } catch (IOException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new IOException("Failed to serialize event to JSON", ex);
      }
    }

  public static void writeChangeEvents(OutputStream responseStream, Iterable<ChangeEvent> events)
      throws IOException {
    try {
      mapper.writeValue(responseStream, events);
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Failed to serialize events to JSON", ex);
    }
  }

  public static void formatChangeEvents(OutputStream responseStream, Iterable<ChangeEvent> events)
      throws IOException {
    try {
      prettyMapper.writeValue(responseStream, events);
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Failed to serialize events to JSON", ex);
    }
  }

  public static ChangeEvent[] readChangeEvents(InputStream stream, ScanEntityValidator validator)
      throws IOException {
    try {
      JsonNode nextNode = mapper.readTree(stream);
      if (nextNode instanceof ArrayNode) {
        ChangeEvent[] events = mapper.convertValue(nextNode, ChangeEvent[].class);
        for(ChangeEvent event: events) validator.process(event);
        return events;
      } else {
        ChangeEvent event = mapper.convertValue(nextNode, ChangeEvent.class);
        validator.process(event);
        return new ChangeEvent[] { event };
      }
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Failed to deserialize event from JSON", ex);
    }
  }

}
