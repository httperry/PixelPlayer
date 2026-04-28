@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.mozilla:rhino:1.7.14")

import org.mozilla.javascript.Context

val ctx = Context.enter()
ctx.optimizationLevel = -1 // Interpreter mode
val scope = ctx.initStandardObjects()
val result = ctx.evaluateString(scope, "var a = 'hello'; a + ' world';", "test", 1, null)
println("Rhino says: $result")
Context.exit()
