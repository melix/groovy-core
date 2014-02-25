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

import groovy.lang.Closure;
import groovy.lang.Writable;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import static groovy.xml.XmlUtil.escapeXml;

public abstract class BaseTemplate implements Writable {
    private final static Map EMPTY_MODEL = Collections.emptyMap();

    private final Map model;
    private final Map modelTypes;
    private final MarkupTemplateEngine engine;
    private final TemplateConfiguration configuration;

    private Writer out;
    private boolean doWriteIndent;

    public BaseTemplate(final MarkupTemplateEngine templateEngine, final Map model, final Map modelTypes, final TemplateConfiguration configuration) {
        this.model = model==null?EMPTY_MODEL:model;
        this.engine = templateEngine;
        this.configuration = configuration;
        this.modelTypes = modelTypes;
    }

    public Map getModel() {
        return model;
    }

    public abstract Object run();

    public BaseTemplate yieldUnescaped(Object obj) throws IOException {
        writeIndent();
        out.write(obj.toString());
        return this;
    }

    public BaseTemplate yield(Object obj) throws IOException {
        writeIndent();
        out.write(escapeXml(obj.toString()));
        return this;
    }

    public BaseTemplate comment(Object cs) throws IOException {
        writeIndent();
        out.write("<!--");
        out.write(cs.toString());
        out.write("-->");
        return this;
    }

    public BaseTemplate xmlDeclaration() throws IOException {
        out.write("<?xml ");
        writeAttribute("version", "1.0");
        if (configuration.getDeclarationEncoding() != null) {
            writeAttribute("encoding", configuration.getDeclarationEncoding());
        }
        out.write("?>\n");
        return this;
    }

    public BaseTemplate pi(Map<?, ?> attrs) throws IOException {
        for (Map.Entry<?, ?> entry : attrs.entrySet()) {
            Object target = entry.getKey();
            Object instruction = entry.getValue();
            out.write("<?");
            out.write(target.toString());
            if (instruction instanceof Map) {
                writeAttributes((Map) instruction);
            } else {
                out.write(target.toString());
                out.write(" ");
                out.write(instruction.toString());
            }
            out.write("?>");
        }
        return this;
    }

    private void writeAttribute(String attName, String value) throws IOException {
        out.write(attName);
        out.write("=");
        writeQt();
        out.write(escapeQuotes(value));
        writeQt();
    }

    private void writeQt() throws IOException {
        if (configuration.isUseDoubleQuotes()) {
            out.write('"');
        } else {
            out.write('\'');
        }
    }

    private void writeIndent() throws IOException {
        if (out instanceof DelegatingIndentWriter && doWriteIndent) {
            ((DelegatingIndentWriter)out).writeIndent();
            doWriteIndent = false;
        }
    }

    private String escapeQuotes(String str) {
        String quote = configuration.isUseDoubleQuotes() ? "\"" : "'";
        String escape = configuration.isUseDoubleQuotes() ? "&quote;" : "&apos;";
        return str.replace(quote, escape);
    }

    public Object methodMissing(String tagName, Object args) throws IOException {
        Object o = model.get(tagName);
        if (o instanceof Closure) {
            if (args instanceof Object[]) {
                yieldUnescaped(((Closure) o).call((Object[])args));
                return this;
            }
            yieldUnescaped(((Closure) o).call(args));
            return this;
        } else if (args instanceof Object[]) {
            final Writer wrt = out;
            TagData tagData = new TagData(args).invoke();
            Object body = tagData.getBody();
            writeIndent();
            wrt.write('<');
            wrt.write(tagName);
            writeAttributes(tagData.getAttributes());
            if (body != null) {
                wrt.write('>');
                writeBody(body);
                writeIndent();
                wrt.write("</");
                wrt.write(tagName);
                wrt.write('>');
            } else {
                if (configuration.isExpandEmptyElements()) {
                    wrt.write("></");
                    wrt.write(tagName);
                    wrt.write('>');
                } else {
                    wrt.write("/>");
                }
            }
        }
        return this;
    }

    private void writeBody(final Object body) throws IOException {
        boolean indent = out instanceof DelegatingIndentWriter;
        if (body instanceof Closure) {
            if (indent) {
                ((DelegatingIndentWriter)(out)).next();
            }
            ((Closure) body).call();
            if (indent) {
                ((DelegatingIndentWriter)(out)).previous();
            }
        } else {
            out.write(body.toString());
        }
    }

    private void writeAttributes(final Map<?, ?> attributes) throws IOException {
        if (attributes == null) {
            return;
        }
        final Writer wrt = out;
        for (Map.Entry entry : attributes.entrySet()) {
            wrt.write(' ');
            String attName = entry.getKey().toString();
            String value = entry.getValue() == null ? "" : entry.getValue().toString();
            writeAttribute(attName, value);
        }
    }

    public void includeGroovy(String templatePath) throws IOException, ClassNotFoundException {
        URL resource = getIncludedResource(templatePath);
        engine.createTemplate(resource, modelTypes).make(model).writeTo(out);
    }

    private URL getIncludedResource(final String templatePath) throws IOException {
        URL resource = engine.getTemplateLoader().getResource(templatePath);
        if (resource == null) {
            throw new IOException("Unable to load template:" + templatePath);
        }
        return resource;
    }

    public void includeEscaped(String templatePath) throws IOException {
        URL resource = getIncludedResource(templatePath);
        yield(ResourceGroovyMethods.getText(resource, engine.getCompilerConfiguration().getSourceEncoding()));
    }

    public void includeUnescaped(String templatePath) throws IOException {
        URL resource = getIncludedResource(templatePath);
        yieldUnescaped(ResourceGroovyMethods.getText(resource, engine.getCompilerConfiguration().getSourceEncoding()));
    }

    public Object tryEscape(Object contents) {
        if (contents instanceof CharSequence) {
            return escapeXml(contents.toString());
        }
        return contents;
    }

    public Writer getOut() {
        return out;
    }

    public void newLine() throws IOException {
        yieldUnescaped(configuration.getNewLineString());
        doWriteIndent = true;
    }

    public Writer writeTo(final Writer out) throws IOException {
        try {
            this.out = createWriter(out);
            run();
            return out;
        } finally {
            this.out.flush();
            this.out = null;
        }
    }

    private Writer createWriter(final Writer out) {
        return configuration.isAutoIndent() && !(out instanceof DelegatingIndentWriter)?new DelegatingIndentWriter(out, configuration.getAutoIndentString()):out;
    }

    private class TagData {
        private final Object[] array;
        private Map attributes;
        private Object body;

        public TagData(final Object args) {
            this.array = (Object[])args;
        }

        public Map getAttributes() {
            return attributes;
        }

        public Object getBody() {
            return body;
        }

        public TagData invoke() {
            attributes = null;
            body = null;
            for (Object o : array) {
                if (o instanceof Map) {
                    attributes = (Map) o;
                } else {
                    body = o;
                }
            }
            return this;
        }
    }
}
