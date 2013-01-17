package net.lshift.diffa.adapter.scanning;

public interface HttpRequestParameters {
  String getParameter(String name);
  String[] getParameterValues(String attrName);
}
