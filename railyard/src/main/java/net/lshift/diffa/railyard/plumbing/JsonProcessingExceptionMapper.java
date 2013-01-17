package net.lshift.diffa.railyard.plumbing;

import org.codehaus.jackson.JsonProcessingException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {

  @Override
  public Response toResponse(JsonProcessingException e) {
    String msg = "Unknown JSON processing error";
    String[] lines = e.getMessage().split("\n");
    if (lines != null && lines.length > 0) {
      msg = lines[0];
    }
    return Response.status(400).entity(msg).build();
  }
}
