package net.lshift.diffa.railyard.plumbing;

import net.lshift.diffa.adapter.scanning.HttpRequestParameters;
import org.jboss.resteasy.spi.HttpRequest;

import javax.ws.rs.core.MultivaluedMap;

public class RestEasyRequestWrapper implements HttpRequestParameters {

  private MultivaluedMap<String,String> parameters;

  public RestEasyRequestWrapper(HttpRequest request) {
    parameters = request.getUri().getQueryParameters();
  }

  @Override
  public String getParameter(String name) {
    return parameters.getFirst(name);
  }

  @Override
  public String[] getParameterValues(String attrName) {
    return parameters.get(attrName).toArray(new String[0]);
  }
}
