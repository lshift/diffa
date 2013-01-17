package net.lshift.diffa.adapter.scanning;

import javax.servlet.http.HttpServletRequest;

public class ServletRequestParametersWrapper implements HttpRequestParameters {

  private HttpServletRequest underlyingRequest;

  public ServletRequestParametersWrapper(HttpServletRequest request) {
    this.underlyingRequest = request;
  }

  @Override
  public String getParameter(String name) {
    return underlyingRequest.getParameter(name);
  }

  @Override
  public String[] getParameterValues(String attrName) {
    return underlyingRequest.getParameterValues(attrName);
  }
}
