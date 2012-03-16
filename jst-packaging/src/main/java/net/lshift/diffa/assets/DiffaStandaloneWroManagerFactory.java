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

import ro.isdc.wro.extensions.manager.ExtensionsConfigurableWroManagerFactory;
import ro.isdc.wro.manager.factory.standalone.ConfigurableStandaloneContextAwareManagerFactory;
import ro.isdc.wro.model.resource.processor.ResourcePostProcessor;
import ro.isdc.wro.model.resource.processor.ResourcePreProcessor;

import java.util.Map;
import java.util.Properties;

/**
 * Custom factory allowing html templates to be used as a pre-processor.
 */
public class DiffaStandaloneWroManagerFactory extends ConfigurableStandaloneContextAwareManagerFactory {
  @Override
  protected Map<String, ResourcePreProcessor> createPreProcessorsMap() {
    Map<String, ResourcePreProcessor> result = super.createPreProcessorsMap();

    ExtensionsConfigurableWroManagerFactory.pupulateMapWithExtensionsProcessors(result);
    result.put("jstTemplates", new JstTemplatesPreProcessor());

    return result;
  }

  @Override
  protected Map<String, ResourcePostProcessor> createPostProcessorsMap() {
    Map<String, ResourcePostProcessor> result = super.createPostProcessorsMap();

    ExtensionsConfigurableWroManagerFactory.pupulateMapWithExtensionsProcessors(result);

    return result;
  }

  @Override
  protected Properties createProperties() {
    final Properties props = super.createProperties();

    props.put("preProcessors", "jstTemplates,cssUrlRewriting,cssImport,semicolonAppender");
    props.put("postProcessors", "jsMin,lessCss");

    return props;
  }
}