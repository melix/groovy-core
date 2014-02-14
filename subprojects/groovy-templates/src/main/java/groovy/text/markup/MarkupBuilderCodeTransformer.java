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

import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ArrayExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.SourceUnit;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MarkupBuilderCodeTransformer extends ClassCodeExpressionTransformer {

    private final SourceUnit unit;
    private final boolean autoEscape;

    public MarkupBuilderCodeTransformer(final SourceUnit unit, final boolean autoEscape) {
        this.unit = unit;
        this.autoEscape = autoEscape;
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return unit;
    }

    @Override
    public Expression transform(final Expression exp) {
        if (exp instanceof MethodCallExpression) {
            return transformMethodCall((MethodCallExpression) exp);
        }
        if (exp instanceof ClosureExpression) {
            ClosureExpression cl = (ClosureExpression) exp;
            cl.getCode().visit(this);
        }
        if (exp instanceof VariableExpression) {
            VariableExpression var = (VariableExpression) exp;
            if (var.getAccessedVariable() instanceof DynamicVariable) {
                MethodCallExpression callGetModel = new MethodCallExpression(
                        new VariableExpression("this"),
                        "getModel",
                        ArgumentListExpression.EMPTY_ARGUMENTS
                );
                callGetModel.setImplicitThis(true);
                callGetModel.setSourcePosition(exp);
                String varName = var.getName();
                if ("model".equals(varName)) {
                    return callGetModel;
                }
                MethodCallExpression mce = new MethodCallExpression(
                        callGetModel,
                        "get",
                        new ArgumentListExpression(new ConstantExpression(varName))
                );
                mce.setSourcePosition(exp);
                mce.setImplicitThis(false);
                MethodCallExpression yield = new MethodCallExpression(
                        new VariableExpression("this"),
                        "tryEscape",
                        new ArgumentListExpression(mce)
                );
                yield.setImplicitThis(true);
                yield.setSourcePosition(exp);
                return autoEscape?yield:mce;
            }
        }
        return super.transform(exp);
    }

    private Expression transformMethodCall(final MethodCallExpression exp) {
        String name = exp.getMethodAsString();
        if (exp.isImplicitThis() && "include".equals(name)) {
            return tryTransformInclude(exp);
        } else if (exp.isImplicitThis() && name.startsWith(":")) {
            List<Expression> args;
            if (exp.getArguments() instanceof ArgumentListExpression) {
                args = ((ArgumentListExpression) exp.getArguments()).getExpressions();
            } else {
                args = Collections.singletonList(exp.getArguments());
            }
            Expression newArguments = transform(new ArgumentListExpression(new ConstantExpression(name.substring(1)), new ArrayExpression(ClassHelper.OBJECT_TYPE, args)));
            MethodCallExpression call = new MethodCallExpression(
                    new VariableExpression("this"),
                    "methodMissing",
                    newArguments
            );
            call.setImplicitThis(true);
            call.setSafe(exp.isSafe());
            call.setSpreadSafe(exp.isSpreadSafe());
            call.setSourcePosition(exp);
            return call;
        }
        return super.transform(exp);
    }

    private Expression tryTransformInclude(final MethodCallExpression exp) {
        Expression arguments = exp.getArguments();
        if (arguments instanceof TupleExpression) {
            List<Expression> expressions = ((TupleExpression) arguments).getExpressions();
            if (expressions.size() == 1 && expressions.get(0) instanceof MapExpression) {
                MapExpression map = (MapExpression) expressions.get(0);
                List<MapEntryExpression> entries = map.getMapEntryExpressions();
                if (entries.size() == 1) {
                    MapEntryExpression mapEntry = entries.get(0);
                    Expression keyExpression = mapEntry.getKeyExpression();
                    try {
                        IncludeType includeType = IncludeType.valueOf(keyExpression.getText().toLowerCase());
                        MethodCallExpression call = new MethodCallExpression(
                                exp.getObjectExpression(),
                                includeType.getMethodName(),
                                new ArgumentListExpression(
                                        mapEntry.getValueExpression()
                                )
                        );
                        call.setImplicitThis(true);
                        call.setSafe(exp.isSafe());
                        call.setSpreadSafe(exp.isSpreadSafe());
                        call.setSourcePosition(exp);
                        return call;
                    } catch (IllegalArgumentException e) {
                        // not a valid import type, do not modify the code
                    }
                }

            }
        }
        return super.transform(exp);
    }
}
