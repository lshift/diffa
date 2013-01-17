package net.lshift.diffa.railyard.plumbing;

import org.jboss.resteasy.spi.MethodNotAllowedException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

public class MethodNotAllowedExceptionMapper implements ExceptionMapper<MethodNotAllowedException> {
  @Override
  public Response toResponse(MethodNotAllowedException e) {
    return Response.status(405).build();
  }
}
