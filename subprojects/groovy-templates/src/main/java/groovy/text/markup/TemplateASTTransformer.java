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

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.classgen.VariableScopeVisitor;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.objectweb.asm.Opcodes;

class TemplateASTTransformer extends CompilationCustomizer {
    public TemplateASTTransformer() {
        super(CompilePhase.SEMANTIC_ANALYSIS);
    }

    @Override
    public void call(final SourceUnit source, final GeneratorContext context, final ClassNode classNode) throws CompilationFailedException {
        if (classNode.isScriptBody()) {
            classNode.setSuperClass(MarkupTemplateEngine.BASETEMPLATE_CLASSNODE);
            createConstructor(classNode);
            transformRunMethod(classNode, source);
            VariableScopeVisitor visitor = new VariableScopeVisitor(source);
            visitor.visitClass(classNode);
        }
    }

    private void transformRunMethod(final ClassNode classNode, final SourceUnit source) {
        MethodNode runMethod = classNode.getDeclaredMethod("run", Parameter.EMPTY_ARRAY);
        Statement code = runMethod.getCode();
        MarkupBuilderCodeTransformer transformer = new MarkupBuilderCodeTransformer(source);
        code.visit(transformer);
        ClosureExpression cl = new ClosureExpression(Parameter.EMPTY_ARRAY, code);
        runMethod.setCode(new ExpressionStatement(cl));
    }

    private void createConstructor(final ClassNode classNode) {
        Parameter[] params = new Parameter[]{
                new Parameter(MarkupTemplateEngine.MARKUPTEMPLATEENGINE_CLASSNODE, "engine"),
                new Parameter(ClassHelper.MAP_TYPE.getPlainNodeReference(), "model")
        };
        ExpressionStatement body = new ExpressionStatement(
                new ConstructorCallExpression(ClassNode.SUPER,
                        new ArgumentListExpression(new VariableExpression(params[0]), new VariableExpression(params[1])))
        );
        ConstructorNode ctor = new ConstructorNode(Opcodes.ACC_PUBLIC, params, ClassNode.EMPTY_ARRAY, body);
        classNode.addConstructor(ctor);
    }
}
