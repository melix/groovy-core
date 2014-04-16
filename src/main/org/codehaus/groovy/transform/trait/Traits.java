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
package org.codehaus.groovy.transform.trait;

import groovy.lang.GeneratedGroovyProxy;
import groovy.transform.ForceOverride;
import groovy.transform.Trait;
import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.classgen.asm.BytecodeHelper;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * A collection of utility methods used to deal with traits.
 *
 * @author Cédric Champeau
 * @since 2.3.0
 */
public abstract class Traits {
    public static final ClassNode FORCEOVERRIDE_CLASSNODE = ClassHelper.make(ForceOverride.class);
    public static final ClassNode IMPLEMENTED_CLASSNODE = ClassHelper.make(Implemented.class);
    public static final ClassNode TRAITBRIDGE_CLASSNODE = ClassHelper.make(TraitBridge.class);
    public static final Class TRAIT_CLASS = Trait.class;
    public static final ClassNode TRAIT_CLASSNODE = ClassHelper.make(TRAIT_CLASS);
    public static final ClassNode GENERATED_PROXY_CLASSNODE = ClassHelper.make(GeneratedGroovyProxy.class);

    static final String TRAIT_TYPE_NAME = "@" + TRAIT_CLASSNODE.getNameWithoutPackage();
    static final String TRAIT_HELPER = "$Trait$Helper";
    static final String FIELD_HELPER = "$Trait$FieldHelper";
    static final String DIRECT_SETTER_SUFFIX = "$set";
    static final String DIRECT_GETTER_SUFFIX = "$get";
    static final String INIT_METHOD = "$init$";
    static final String STATIC_INIT_METHOD = "$static$init$";
    static final String THIS_OBJECT = "$self";
    static final String STATIC_THIS_OBJECT = "$static$self";
    static final String STATIC_FIELD_PREFIX = "$static";

    static final String SUPER_TRAIT_METHOD_PREFIX = "trait$super$";

    static String fieldHelperClassName(final ClassNode traitNode) {
        return traitNode.getName() + FIELD_HELPER;
    }

    static String helperGetterName(final FieldNode field) {
        return remappedFieldName(unwrapOwner(field.getOwner()), field.getName()) + DIRECT_GETTER_SUFFIX;
    }

    static String helperSetterName(final FieldNode field) {
        return remappedFieldName(unwrapOwner(field.getOwner()), field.getName()) + DIRECT_SETTER_SUFFIX;
    }

    static String helperClassName(final ClassNode traitNode) {
        return traitNode.getName() + TRAIT_HELPER;
    }

    static String remappedFieldName(final ClassNode traitNode, final String name) {
        return traitNode.getName().replace('.','_')+"__"+name;
    }

    private static ClassNode unwrapOwner(ClassNode owner) {
        if (ClassHelper.CLASS_Type.equals(owner) && owner.getGenericsTypes()!=null && owner.getGenericsTypes().length==1) {
            return owner.getGenericsTypes()[0].getType();
        }
        return owner;
    }

    static TraitHelpersTuple findHelpers(final ClassNode trait) {
        ClassNode helperClassNode = null;
        ClassNode fieldHelperClassNode = null;
        Iterator<InnerClassNode> innerClasses = trait.redirect().getInnerClasses();
        if (innerClasses != null && innerClasses.hasNext()) {
            // trait defined in same source unit
            while (innerClasses.hasNext()) {
                ClassNode icn = innerClasses.next();
                if (icn.getName().endsWith(Traits.FIELD_HELPER)) {
                    fieldHelperClassNode = icn;
                } else if (icn.getName().endsWith(Traits.TRAIT_HELPER)) {
                    helperClassNode = icn;
                }
            }
        } else {
            // precompiled trait
            try {
                final ClassLoader classLoader = trait.getTypeClass().getClassLoader();
                String helperClassName = Traits.helperClassName(trait);
                helperClassNode = ClassHelper.make(classLoader.loadClass(helperClassName));
                try {
                    fieldHelperClassNode = ClassHelper.make(classLoader.loadClass(Traits.fieldHelperClassName(trait)));
                } catch (ClassNotFoundException e) {
                    // not a problem, the field helper may be absent
                }
            } catch (ClassNotFoundException e) {
                throw new GroovyBugError("Couldn't find trait helper classes on compile classpath!",e);
            }
        }
        return new TraitHelpersTuple(helperClassNode,  fieldHelperClassNode);
    }

    /**
     * Returns true if the specified class node is a trait.
     * @param cNode a class node to test
     * @return true if the classnode represents a trait
     */
    public static boolean isTrait(final ClassNode cNode) {
        return cNode!=null
                && ((cNode.isInterface() && !cNode.getAnnotations(TRAIT_CLASSNODE).isEmpty())
                    || isAnnotatedWithTrait(cNode));
    }

    /**
     * Returns true if the specified class is a trait.
     * @param clazz a class to test
     * @return true if the classnode represents a trait
     */
    public static boolean isTrait(final Class clazz) {
        return clazz!=null && clazz.getAnnotation(Trait.class)!=null;
    }


    /**
     * Returns true if the specified class node is annotated with the {@link Trait} interface.
     * @param cNode a class node
     * @return true if the specified class node is annotated with the {@link Trait} interface.
     */
    public static boolean isAnnotatedWithTrait(final ClassNode cNode) {
        List<AnnotationNode> traitAnn = cNode.getAnnotations(Traits.TRAIT_CLASSNODE);
        return traitAnn != null && !traitAnn.isEmpty();
    }

    /**
     * Returns true if the specified method node is annotated with {@link ForceOverride}
     * @param methodNode a method node
     * @return  true if the specified method node is annotated with {@link ForceOverride}
     */
    public static boolean isForceOverride(final MethodNode methodNode) {
        return !methodNode.getAnnotations(FORCEOVERRIDE_CLASSNODE).isEmpty()
                || !methodNode.getDeclaringClass().getAnnotations(FORCEOVERRIDE_CLASSNODE).isEmpty();
    }

    /**
     * Returns true if the specified method is annotated with {@link ForceOverride}
     * @param methodNode a method
     * @return  true if the specified method is annotated with {@link ForceOverride}
     */
    public static boolean isForceOverride(final Method methodNode) {
        return methodNode.getAnnotation(ForceOverride.class)!=null ||
                methodNode.getDeclaringClass().getAnnotation(ForceOverride.class)!=null ;
    }

    /**
     * Indicates whether a method in a trait interface has a default implementation.
     * @param method a method node
     * @return true if the method has a default implementation in the trait
     */
    public static boolean hasDefaultImplementation(final MethodNode method) {
        return !method.getAnnotations(IMPLEMENTED_CLASSNODE).isEmpty();
    }

    /**
     * Reflection API to indicate whether some method is a bridge method to the default implementation
     * of a trait.
     * @param someMethod a method node
     * @return null if it is not a method implemented in a trait. If it is, returns the method from the trait class.
     */
    public static boolean isBridgeMethod(Method someMethod) {
        TraitBridge annotation = someMethod.getAnnotation(TraitBridge.class);
        return annotation!=null;
    }

    /**
     * Reflection API to find the method corresponding to the default implementation of a trait, given a bridge method.
     * @param someMethod a method node
     * @return null if it is not a method implemented in a trait. If it is, returns the method from the trait class.
     */
    public static Method getBridgeMethodTarget(Method someMethod) {
        TraitBridge annotation = someMethod.getAnnotation(TraitBridge.class);
        if (annotation==null) {
            return null;
        }
        Class aClass = annotation.traitClass();
        String desc = annotation.desc();
        for (Method method : aClass.getDeclaredMethods()) {
            String methodDescriptor = BytecodeHelper.getMethodDescriptor(method.getReturnType(), method.getParameterTypes());
            if (desc.equals(methodDescriptor)) {
                return method;
            }
        }
        return null;
    }


    /**
     * Converts a class implementing some trait into a target class. If the trait is a dynamic proxy and
     * that the target class is assignable to the target object of the proxy, then the target object is
     * returned. Otherwise, falls back to {@link org.codehaus.groovy.runtime.DefaultGroovyMethods#asType(java.lang.Object, Class)}
     * @param self an object to be coerced to some class
     * @param clazz the class to be coerced to
     * @return the object coerced to the target class, or the proxy instance if it is compatible with the target class.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getAsType(Object self, Class<T> clazz) {
        if (self instanceof GeneratedGroovyProxy) {
            Object proxyTarget = ((GeneratedGroovyProxy)self).getProxyTarget();
            if (clazz.isAssignableFrom(proxyTarget.getClass())) {
                return (T) proxyTarget;
            }
        }
        return DefaultGroovyMethods.asType(self, clazz);
    }

    /**
     * Returns the name of a method without the super trait specific prefix. If the method name
     * doesn't correspond to a super trait method call, the result will be null.
     * @param origName the name of a method
     * @return null if the name doesn't start with the super trait prefix, otherwise the name without the prefix
     */
    public static String getNameWithoutSuperTrait(String origName) {
        if (origName.startsWith(SUPER_TRAIT_METHOD_PREFIX)) {
            return origName.substring(SUPER_TRAIT_METHOD_PREFIX.length());
        }
        return null;
    }

    /**
     * Collects all interfaces of a class node, but reverses the order of the declaration of direct interfaces
     * of this class node. This is used to make sure a trait implementing A,B where both A and B have the same
     * method will take the method from B (latest), aligning the behavior with categories.
     * @param cNode a class node
     * @param interfaces ordered set of interfaces
     */
    public static LinkedHashSet<ClassNode> collectAllInterfacesReverseOrder(ClassNode cNode, LinkedHashSet<ClassNode> interfaces) {
        if (cNode.isInterface())
            interfaces.add(cNode);

        ClassNode[] directInterfaces = cNode.getInterfaces();
        for (int i = directInterfaces.length-1; i >=0 ; i--) {
            final ClassNode anInterface = directInterfaces[i];
            interfaces.add(anInterface);
            collectAllInterfacesReverseOrder(anInterface, interfaces);
        }
        return interfaces;
    }

    /**
     * Internal annotation used to indicate which methods in a trait interface have a
     * default implementation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Implemented {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)

    /**
     * Internal annotation used to indicate that a method is a bridge method to a trait
     * default implementation.
     */
     public static @interface TraitBridge {
        /**
         * @return the trait class
         */
        Class traitClass();

        /**
         * @return The method descriptor of the method from the trait
         */
        String desc();
    }
}
