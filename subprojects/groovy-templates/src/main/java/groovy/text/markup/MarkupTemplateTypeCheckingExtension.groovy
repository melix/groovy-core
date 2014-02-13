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

package groovy.text.markup

import org.codehaus.groovy.ast.ClassCodeExpressionTransformer
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ArrayExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.control.SourceUnit

import static org.codehaus.groovy.ast.ClassHelper.*
import org.codehaus.groovy.transform.stc.GroovyTypeCheckingExtensionSupport


class MarkupTemplateTypeCheckingExtension extends GroovyTypeCheckingExtensionSupport.TypeCheckingDSL {
    @Override
    Object run() {
        beforeVisitMethod {
            newScope {
                builderCalls = []
            }
        }
        methodNotFound { receiver, name, argList, argTypes, call ->
            if (call.lineNumber>0) {
                if (call.implicitThis && argTypes && argTypes[-1] == CLOSURE_TYPE) {
                    currentScope.builderCalls << call
                }
                return makeDynamic(call, OBJECT_TYPE)
            }
        }
        unresolvedProperty { pexp ->
            makeDynamic(pexp)
        }
        unresolvedAttribute { pexp ->
            makeDynamic(pexp)
        }
        afterVisitMethod { mn ->
            scopeExit {
                new BuilderMethodReplacer(context.source, builderCalls).visitMethod(mn)
            }
        }
    }

    private static class BuilderMethodReplacer extends ClassCodeExpressionTransformer {

        private static final MethodNode METHOD_MISSING = ClassHelper.make(BaseTemplate).getMethods('methodMissing')[0]

        private final SourceUnit unit;
        private final Set<MethodCallExpression> callsToBeReplaced;

        BuilderMethodReplacer(SourceUnit unit, Collection<MethodCallExpression> calls) {
            this.unit = unit
            this.callsToBeReplaced = calls as Set;
        }

        @Override
        protected SourceUnit getSourceUnit() {
            unit
        }

        @Override
        public Expression transform(final Expression exp) {
            if (callsToBeReplaced.contains(exp)) {
                def args = exp.arguments instanceof ArgumentListExpression?exp.arguments.expressions:[exp.arguments]
                // replace with direct call to methodMissing
                def call = new MethodCallExpression(
                        new VariableExpression("this"),
                        "methodMissing",
                        new ArgumentListExpression(
                                new ConstantExpression(exp.getMethodAsString()),
                                new ArrayExpression(
                                        OBJECT_TYPE,
                                        [*args]
                                )
                        )
                )
                call.implicitThis = true
                call.safe = exp.safe
                call.spreadSafe = exp.spreadSafe
                call.methodTarget = METHOD_MISSING
                call
            } else {
                super.transform(exp)
            }
        }
    }
}
