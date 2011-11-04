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
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Helper for serialising scanning results in JSON.
 */
public class JSONHelper {
  private static ObjectMapper mapper = new ObjectMapper();
  private static ObjectMapper prettyMapper = new ObjectMapper();
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
      throw new IOException("Failed to serialise result to JSON", ex);
    }
  }

  public static void formatQueryResult(OutputStream responseStream, Iterable<ScanResultEntry> entries)
      throws IOException {
    try {
      prettyMapper.writeValue(responseStream, entries);
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Failed to serialise result to JSON", ex);
    }
  }

  public static ScanResultEntry[] readQueryResult(InputStream stream)
      throws IOException {
    try {
      return mapper.readValue(stream, ScanResultEntry[].class);
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Failed to deserialise result from JSON", ex);
    }
  }

  public static void writeProcessingResponse(OutputStream responseStream, ProcessingResponse response)
      throws IOException {
    try {
      mapper.writeValue(responseStream, response);
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Failed to serialise result to JSON", ex);
    }
  }

  public static ProcessingResponse readProcessingResponse(InputStream stream)
      throws IOException {
    try {
      return mapper.readValue(stream, ProcessingResponse.class);
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Failed to deserialise result from JSON", ex);
    }
  }
}
