package net.lshift.diffa.railyard.plumbing;

import net.lshift.diffa.versioning.VersionStoreException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

public class VersionStoreExceptionMapper implements ExceptionMapper<VersionStoreException> {

  @Override
  public Response toResponse(VersionStoreException e) {
    return Response.status(500).build();
  }
}
