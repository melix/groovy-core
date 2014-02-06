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

package groovy.text

import groovy.text.markup.MarkupTemplateEngine

class MarkupTemplateEngineTest extends GroovyTestCase {
    void testSimpleTemplate() {
        MarkupTemplateEngine engine = new MarkupTemplateEngine()
        def template = engine.createTemplate '''
html {
    body {
        mkp.yield 'It works!'
    }
}
'''
        StringWriter rendered = new StringWriter()
        template.make().writeTo(rendered)
        assert rendered.toString() == '<html><body>It works!</body></html>'
    }

    void testSimpleTemplateWithModel() {
        MarkupTemplateEngine engine = new MarkupTemplateEngine()
        def template = engine.createTemplate '''
html {
    body {
        mkp.yield message
    }
}
'''
        def model = [message: 'It works!']
        StringWriter rendered = new StringWriter()
        template.make(model).writeTo(rendered)
        assert rendered.toString() == '<html><body>It works!</body></html>'
    }

    void testSimpleTemplateWithInclude() {
        MarkupTemplateEngine engine = new MarkupTemplateEngine()
        def template = engine.createTemplate '''
html {
    body {
        include 'includes/hello.tpl'
    }
}
'''
        StringWriter rendered = new StringWriter()
        template.make().writeTo(rendered)
        assert rendered.toString() == '<html><body>Hello from include!</body></html>'
    }

    void testCollectionInModel() {
        MarkupTemplateEngine engine = new MarkupTemplateEngine()
        def template = engine.createTemplate '''
html {
    body {
        ul {
            persons.each { p ->
                li(p.name)
            }
        }
    }
}
'''
        StringWriter rendered = new StringWriter()
        def model = [persons:[[name:'Cedric'],[name:'Jochen']]]
        template.make(model).writeTo(rendered)
        assert rendered.toString() == '<html><body><ul><li>Cedric</li><li>Jochen</li></ul></body></html>'

    }
}
