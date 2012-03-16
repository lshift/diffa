/**
 * Copyright (C) 2012 LShift Ltd.
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
package net.lshift.diffa.assets;

import org.apache.commons.lang.StringUtils;
import ro.isdc.wro.model.resource.processor.ResourcePreProcessor;
import ro.isdc.wro.model.resource.Resource;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;

/**
 * Pre-processes .html files, turning them into javascript code suitable for embedding.
 */
public class JstTemplatesPreProcessor implements ResourcePreProcessor {
  public void process(Resource resource, Reader input, Writer output) throws IOException {
    if (resource.getUri().endsWith(".jst")) {
      String content = IOUtils.toString(input);
      String name = removeSuffix(removePrefix(resource.getUri(), "/js/templates/"), ".jst");

      output.write("window.JST = (window.JST || {});\n");
      output.write(String.format("window.JST['%s'] = _.template(\n", name));

      List<String> result = new ArrayList<String>();
      for (String l : content.split("\n")) {
        result.add("\"" + StringEscapeUtils.escapeJava(l) + "\"");
      }
      output.write(StringUtils.join(result.iterator(), " + \n"));
      output.write(");\n");
    } else {
      IOUtils.copy(input, output);
    }
  }

  protected String removePrefix(String s, String prefix) {
    if (s.startsWith(prefix)) {
      return s.substring(prefix.length());
    } else {
      return s;
    }
  }

  public String removeSuffix(String s, String suffix) {
    if (s.endsWith(suffix)) {
      return s.substring(0, s.length() - suffix.length());
    } else {
      return s;
    }
  }
}