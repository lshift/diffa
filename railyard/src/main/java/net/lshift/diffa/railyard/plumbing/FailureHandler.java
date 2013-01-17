package net.lshift.diffa.railyard.plumbing;

import org.jboss.resteasy.spi.ApplicationException;
import org.jboss.resteasy.spi.BadRequestException;
import org.jboss.resteasy.spi.Failure;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

public class FailureHandler implements ExceptionMapper<Failure> {

  @Override
  public Response toResponse(Failure failure) {
    return Response.status(failure.getErrorCode()).entity(failure.getMessage()).build();
  }
}
