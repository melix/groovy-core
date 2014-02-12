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

/**
 * Configuration options for the {@link groovy.text.markup.MarkupTemplateEngine markup template engine}.
 *
 * @author Cedric Champeau
 */
public class TemplateConfiguration {

    private String declarationEncoding;
    private boolean expandEmptyElements;
    private boolean useDoubleQuotes;
    private String newLineString = System.getProperty("line.separator");

    /**
     * @return the encoding used in the declaration header
     */
    public String getDeclarationEncoding() {
        return declarationEncoding;
    }

    /**
     * Set the encoding used to write the declaration header. Note that it is the responsability of
     * the user to ensure that it matches the writer encoding.
     * @param declarationEncoding encoding to be used in the declaration string
     */
    public void setDeclarationEncoding(final String declarationEncoding) {
        this.declarationEncoding = declarationEncoding;
    }

    /**
     * @return whether elements without body should be written in the short form (ex: &lt;br/&gt;) or
     * in an expanded form (ex: &lt;br&gt;&lt;/br&gt;)
     */
    public boolean isExpandEmptyElements() {
        return expandEmptyElements;
    }

    public void setExpandEmptyElements(final boolean expandEmptyElements) {
        this.expandEmptyElements = expandEmptyElements;
    }

    /**
     * @return true if attributes should use double quotes instead of single quotes
     */
    public boolean isUseDoubleQuotes() {
        return useDoubleQuotes;
    }

    public void setUseDoubleQuotes(final boolean useDoubleQuotes) {
        this.useDoubleQuotes = useDoubleQuotes;
    }

    public String getNewLineString() {
        return newLineString;
    }

    public void setNewLineString(final String newLineString) {
        this.newLineString = newLineString;
    }
}
