package net.lshift.diffa.system;

import net.lshift.diffa.config.CategoryDescriptor;

import java.util.HashMap;
import java.util.Map;

public class Endpoint {

  /**
   * This class is only designed to be consumed by internal components because it contains non-externalized ids.
   *
   * Having said that, we seem to have re-defined the Endpoint class quite a few times on this project. Given that,
   * going forwards we should work out some kind of sane externalizable and non-extenralizable hierarchy for this stuff.
   *
   */

  private Long id;
  private Map<String, CategoryDescriptor> categories = new HashMap<String, CategoryDescriptor>();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Map<String, CategoryDescriptor> getCategories() {
    return categories;
  }

  public void addCategory(String name, CategoryDescriptor descriptor) {
    categories.put(name, descriptor);
  }
}
