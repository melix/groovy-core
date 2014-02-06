/*
 * Copyright 2003-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package groovy.text.markup;

import groovy.lang.GroovyObject;
import groovy.lang.Writable;
import groovy.xml.StreamingMarkupBuilder;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.codehaus.groovy.runtime.metaclass.MissingPropertyExceptionNoStack;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.Map;

public abstract class BaseTemplate implements Writable {
    private final Map model;
    private final MarkupTemplateEngine engine;
    private final StreamingMarkupBuilder builder;

    public BaseTemplate(final MarkupTemplateEngine templateEngine, final Map model) {
        this.model = model;
        this.engine = templateEngine;
        this.builder = new StreamingMarkupBuilder();
    }

    public abstract /*Closure*/ Object run();


    protected void includeGroovy(GroovyObject mkp, String templatePath) throws IOException, ClassNotFoundException {
        URL resource = getIncludedResource(templatePath);
        mkp.invokeMethod("yieldUnescaped", new Object[]{engine.createTemplate(resource).make(model)});
    }

    private URL getIncludedResource(final String templatePath) throws IOException {
        URL resource = engine.getTemplateLoader().getResource(templatePath);
        if (resource == null) {
            throw new IOException("Unable to load template:" + templatePath);
        }
        return resource;
    }

    protected void includeEscaped(GroovyObject mkp, String templatePath) throws IOException {
        URL resource = getIncludedResource(templatePath);
        mkp.invokeMethod("yield", new Object[]{ResourceGroovyMethods.getText(resource, engine.getConfiguration().getSourceEncoding())});
    }

    protected void includeUnescaped(GroovyObject mkp, String templatePath) throws IOException {
        URL resource = getIncludedResource(templatePath);
        mkp.invokeMethod("yieldUnescaped", new Object[]{ResourceGroovyMethods.getText(resource, engine.getConfiguration().getSourceEncoding())});
    }

    public Object propertyMissing(String name) {
        if (model.containsKey(name)) {
            return model.get(name);
        } else {
            throw new MissingPropertyExceptionNoStack(name, BaseTemplate.class);
        }
    }

    public Writer writeTo(final Writer out) throws IOException {
        Writable writable = builder.bind(run());
        return writable.writeTo(out);
    }
}
