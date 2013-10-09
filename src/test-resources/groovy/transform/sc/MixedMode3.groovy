import groovy.xml.MarkupBuilder

methodNotFound { receiver, name, argumentList, argTypes, call ->
    if (receiver==classNodeFor(MarkupBuilder) && argTypes[-1]==CLOSURE_TYPE) {
        def lastArg = argumentList[-1]
        if (isClosureExpression(lastArg)) {
            makeDynamic(lastArg)
        }
        return makeDynamic(call, OBJECT_TYPE)
    }
}
