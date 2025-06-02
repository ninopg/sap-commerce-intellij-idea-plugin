package ghac

import com.sun.management.ThreadMXBean
import de.hybris.platform.core.model.ItemModel
import de.hybris.platform.jalo.GenericItem
import de.hybris.platform.scripting.engine.ScriptExecutionResult
import de.hybris.platform.scripting.engine.content.impl.SimpleScriptContent
import de.hybris.platform.scripting.engine.exception.ScriptExecutionException
import de.hybris.platform.scripting.engine.impl.PrecompiledExecutable
import groovy.json.DefaultJsonGenerator
import groovy.json.JsonGenerator
import org.apache.catalina.startup.Bootstrap
import org.apache.groovy.json.internal.CharBuf
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.WebApplicationContextUtils
import org.springframework.web.servlet.FrameworkServlet

import javax.script.Compilable
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets

import static de.hybris.platform.hac.scripting.impl.DefaultScriptingLanguageExecutor.*

// REVIEWME: check wirh root context = ''
static Map<String,WebApplicationContext> getSpringWeb() {
    Map<String,WebApplicationContext> map = [:]
    def server = Bootstrap.daemon.catalinaDaemon.server
    server.findServices().each { service ->
        service.container.findChildren().each { host ->
            host.findChildren().each { context ->
                def contextKey = []
                if (service.name != 'Catalina') contextKey << service.name
                if (host.name != service.container.defaultHost) contextKey << host.name
                contextKey << context.baseName

                // spring mvc servlets:
                def mvcServletWrappers = context.findChildren().findAll { it.servlet instanceof FrameworkServlet }
                def rootWebContext = WebApplicationContextUtils.getWebApplicationContext(context.servletContext)

                if (mvcServletWrappers) {
                    mvcServletWrappers.each { mvcServletWrapper ->
                        def attributeName = mvcServletWrapper.servlet.servletContextAttributeName
                        def springContextKey = []
                        if (mvcServletWrappers.size() > 1) {
                            springContextKey = [attributeName - FrameworkServlet.SERVLET_CONTEXT_PREFIX]
                        }
                        // println "context: ${(contextKey + springContextKey).join('/')} -> ${context.servletContext.getAttribute(attributeName)}"
                        map << [('/' + (contextKey + springContextKey).join('/')):context.servletContext.getAttribute(attributeName)]
                    }
                } else if (rootWebContext) {
                    //println "context: ${contextKey.join('/')} -> ${rootWebContext}"
                    map << [('/' + contextKey.join('/')):rootWebContext]
                }
            }
        }
    }
    map
}

String toString(obj) {

    if (obj == null) {
        'null'
    } else if (obj instanceof ItemModel) {
        "${obj.itemtype}(${typeService.getUniqueAttributes(obj.itemtype).collect{"${it}:${toString(modelService.getAttributeValue(obj,it))}"}.join(':')})"
    } else if (obj instanceof GenericItem) {
        toString(modelService.get(obj))
    } else if (obj instanceof Collection) {
        obj.collect{toString(it)}?.toString()
    } else {
        obj?.toString()
    }

}

static String abbreviate(String s, maxLength = 500) {
    if (!s) return s
    s.length() > maxLength ? s.take(maxLength) + '...' : s
}

def toStringConverter = {it -> toString(it)}
def abbreviatedToStringConverter = {it -> abbreviate(toString(toString(it)))}

def jsonGeneratorOptions = new JsonGenerator.Options()
        // java
        .addConverter(java.lang.Class, {it.name})
        .addConverter(org.springframework.beans.factory.BeanFactory, abbreviatedToStringConverter)
        .addConverter(org.springframework.context.ApplicationContext, abbreviatedToStringConverter)
        // aop
        .addConverter(org.springframework.aop.Advisor, toStringConverter)
        .addConverter(org.springframework.aop.TargetSource, toStringConverter)
        .addConverter(org.springframework.cglib.proxy.Callback, toStringConverter)
        // hybris
        .addConverter(de.hybris.platform.core.Tenant, {it.toString()})
        .addConverter(de.hybris.platform.servicelayer.internal.service.AbstractService, toStringConverter)
        .addConverter(de.hybris.platform.core.model.ItemModel, toStringConverter)
        .addConverter(de.hybris.platform.servicelayer.internal.converter.ModelConverter, toStringConverter)
        // exclude nulls
        .excludeNulls()

def jsonGenerator = new DefaultJsonGenerator(jsonGeneratorOptions) {

    Set<Object> visitedObjects = new HashSet<>()

    Integer depth = 0

    Integer maxDepth = 4

    @Override
    protected void writeObject(String key, Object object, CharBuf buffer) {

        // println "${depth} ${key} ${object?.getClass()?.name} ${visitedObjects.size()}"

        depth++

        if (depth > maxDepth || visitedObjects.contains(object)) {
            super.writeObject(key, object?.toString(), buffer)
        } else {
            visitedObjects.add(object)
            super.writeObject(key, object, buffer)
            visitedObjects.remove(object)
        }

        depth--

    }

}

def mapObject = {obj ->
    if (!obj) return ''
    if (obj instanceof String) return obj
    if (obj instanceof Number) return obj
    return jsonGenerator.toJson(obj)
}

def threadMXBean = ManagementFactory.threadMXBean
def thId = Thread.currentThread().getId()
def clockTime = System.nanoTime()
def cpuTime = threadMXBean.currentThreadCpuTime
def allocatedBytes = threadMXBean instanceof com.sun.management.ThreadMXBean ? threadMXBean.getThreadAllocatedBytes(thId) : -1
def result = [:]
def outputStream = new ByteArrayOutputStream()
def stackTraceWriter = new StringWriter()

try {

    def outputPrintStream = new PrintStream(outputStream, true, 'UTF-8')
    def outputWriter = new PrintWriter(outputPrintStream)

    // this adds binding variable 'out'
    def globalContext = scriptingLanguagesExecutor.prepareScriptContext(outputPrintStream)

    // REVIEWME lazy logic here
    def springWeb = getSpringWeb()
    globalContext['springWeb'] = springWeb

    ScriptExecutionResult scriptExecutionResult
    def stackTraceText

    def decodedScript = new String(Base64.decoder.decode('$hacEncodedScript'), StandardCharsets.UTF_8)

    try {

        springWeb.keySet().each{println it}

        def scriptContent = new SimpleScriptContent('groovy', decodedScript)

        def hacSpringWebContext = '$hacSpringWebContext'

        scriptExecutionResult = scriptingLanguagesService.with {

            def scriptExecutable

            if (hacSpringWebContext == 'default') {
                scriptExecutable = getExecutableByContent(new SimpleScriptContent('groovy', decodedScript))
            } else {
                def engine = getEngine(scriptContent.engineName) as Compilable
                def scriptBody = engine.compile(scriptContent.content)
                def applicationContext = springWeb[hacSpringWebContext]
                scriptExecutable = new PrecompiledExecutable(scriptBody, globalContext, applicationContext, null)
            }

            scriptExecutable.execute(globalContext, outputWriter, stackTraceWriter)

        }

    } catch (ScriptExecutionException ex) {
        def clean = true
        def scriptException = ex.cause.cause ?: ex.cause
        def stw = new StringWriter()
        scriptException.printStackTrace(new PrintWriter(stw))
        if (clean) {
            def lines = stw.toString().readLines()
            def cleanedLines = [] as List<String>
            def append = true
            def scriptClassName = this.class.name
            lines.each { line ->
                if (line.contains(scriptClassName)) {
                    // cleanedLines << line
                    cleanedLines << '\t... hac stack'
                    append = false
                } else if (line.startsWith('Caused by:')) {
                    cleanedLines << line
                    append = true
                } else if (append) {
                    cleanedLines << line
                }
            }
            stackTraceText = cleanedLines.join('\n')
        } else {
            stackTraceText = stw.toString()
        }
    }

    // configure json serialization (ex: serialization for models)

    // result[OUTPUT_TEXT_KEY] = scriptingLanguagesExecutor.stringifyOutStream(outputStream)
    result[OUTPUT_TEXT_KEY] = outputStream.toString('UTF-8')
    result[STACKTRACE_TEXT_KEY] = stackTraceText ?: stackTraceWriter.toString()
    result[EXECUTION_RESULT_KEY] = scriptExecutionResult?.scriptResult?.toString() // mapObject(scriptExecutionResult?.scriptResult)
    return mapObject(result)

} catch (Throwable t) {

    // result[OUTPUT_TEXT_KEY] = scriptingLanguagesExecutor.stringifyOutStream(outputStream)
    result[EXECUTION_RESULT_KEY] = ''
    result[OUTPUT_TEXT_KEY] = outputStream.toString('UTF-8')

    if (!stackTraceWriter && !stackTraceWriter.toString()) {
        result[STACKTRACE_TEXT_KEY] = stackTraceWriter.toString()
    } else {
        result[STACKTRACE_TEXT_KEY] = new StringWriter().withWriter {t.printStackTrace(new PrintWriter(it)); it}.toString()
    }
    return mapObject(result)

} finally {

    def elapsedClockTime = System.nanoTime() - clockTime
    def cpuTimeDelta = threadMXBean.currentThreadCpuTime - cpuTime
    def allocatedBytesDelta
    if (threadMXBean instanceof ThreadMXBean) {
        allocatedBytesDelta = allocatedBytes == -1 ? -1 : threadMXBean.getThreadAllocatedBytes(thId) - allocatedBytes
    }
    println "elapsedClockTime: ${sprintf('%.3f',elapsedClockTime/1e6)} ms, cpuTime:${sprintf('%.3f',cpuTimeDelta/1e6)} ms, allocatedMemory: ${"${sprintf('%.3f',allocatedBytesDelta/1024/1024)} MiB"?:'N/A'}"

}