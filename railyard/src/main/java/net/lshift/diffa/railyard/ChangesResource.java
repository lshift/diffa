package net.lshift.diffa.railyard;

import com.google.inject.Inject;
import net.lshift.diffa.events.ChangeEventHandler;
import net.lshift.diffa.events.DefaultTombstoneEvent;
import net.lshift.diffa.versioning.VersionStore;
import net.lshift.diffa.versioning.events.DefaultPartitionedEvent;
import net.lshift.diffa.versioning.events.DefaultUnpartitionedEvent;
import net.lshift.diffa.versioning.events.PartitionedEvent;
import net.lshift.diffa.versioning.events.UnpartitionedEvent;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.jboss.resteasy.spi.BadRequestException;
import org.jboss.resteasy.spi.HttpRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Path("/{space}/changes")
public class ChangesResource {

  static DateTimeFormatter formatter = ISODateTimeFormat.dateTime();

  @Inject private JsonFactory factory;
  @Inject private VersionStore versionStore;

  @POST
  @Path("/{endpoint}")
  public void onChange(@PathParam("endpoint") String endpointName, @Context HttpRequest request) throws IOException {


    Long endpoint = System.currentTimeMillis();

    InputStream is = request.getInputStream();
    JsonParser parser = factory.createJsonParser(is);

    JsonToken current = parser.nextToken();

    if (current == JsonToken.START_OBJECT) {

      propagateEvent(endpoint, parser, versionStore);

    }
    else if (current == JsonToken.START_ARRAY) {

      while ((parser.nextToken()) != JsonToken.END_ARRAY) {

        propagateEvent(endpoint, parser, versionStore);

      }
    }
    else {
      throw new BadRequestException("Malformed JSON input");
    }
  }


  public static void propagateEvent(Long endpoint, JsonParser parser, ChangeEventHandler eventHandler) throws IOException {

    JsonNode node = parser.readValueAsTree();

    final String id = extractMandatoryAttribute(node, "id");
    final String version = extractMandatoryAttribute(node, "version");

    if (version == null || version.isEmpty()) {
      eventHandler.onEvent(endpoint, new DefaultTombstoneEvent(id));
    }
    else {
      String lastUpdateText = extractOptionalAttribute(node, "lastUpdate");
      DateTime lastUpdate = (lastUpdateText == null) ?
          new DateTime(DateTimeZone.UTC) : formatter.parseDateTime(lastUpdateText);

      if (node.has("attributes")) {

        Map<String,String> attributes = new HashMap<String, String>();

        Iterator<Map.Entry<String, JsonNode>> fields = node.get("attributes").getFields();
        while (fields.hasNext()) {
          Map.Entry<String, JsonNode> entry = fields.next();
          attributes.put(entry.getKey(), entry.getValue().getTextValue());
        }

        PartitionedEvent event = new DefaultPartitionedEvent(id, version, lastUpdate, attributes);
        eventHandler.onEvent(endpoint, event);

      }
      else {
        UnpartitionedEvent event = new DefaultUnpartitionedEvent(id, version, lastUpdate);
        eventHandler.onEvent(endpoint, event);
      }

    }

  }

  private static String extractMandatoryAttribute(JsonNode node, String attribute) {
    if (!node.has(attribute)) {
      throw new BadRequestException("Missing mandatory attribute: " + attribute);
    }
    else {
      return node.get(attribute).getTextValue();
    }
  }

  private static String extractOptionalAttribute(JsonNode node, String attribute) {
    JsonNode value = node.get(attribute);
    if (value == null) {
      return null;
    }
    else {
      return value.getTextValue();
    }
  }

}
