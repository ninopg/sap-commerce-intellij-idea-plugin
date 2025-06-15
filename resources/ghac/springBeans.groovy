package ghac

import de.hybris.platform.spring.ctx.CloseAwareApplicationContext
import groovy.json.JsonOutput
import groovy.transform.Field
import org.apache.commons.lang3.reflect.FieldUtils
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.web.context.WebApplicationContext

@Field Map<String,?> resp = [:]
@Field boolean dumpBeans = true

static String contextQualifier(ApplicationContext context) {

    def namespace = context.id

    if (context instanceof WebApplicationContext) {

        def checkPrefix = "org.springframework.web.context.WebApplicationContext:${context.applicationName}"
        if (context.id.startsWith(checkPrefix)) {
            namespace = "${context.applicationName ?: '/ROOT'}${context.id.substring(checkPrefix.length())}".replace('/DispatcherServlet','/springmvc')
        }

    } else if (context instanceof CloseAwareApplicationContext && context.parent) {
        namespace = context.id
    } else if (context instanceof CloseAwareApplicationContext && !context.parent) {
        namespace = 'global'
    }

    namespace

}

def dumpSpringContext(ApplicationContext ctx, boolean  dumpBeans) {

    def _ctx = ctx
    def root = true

    while (_ctx != null) {

        def key = contextQualifier(_ctx)

        def contextProps = {ApplicationContext it ->
            [
                contextId: key,
                id: it.id,
                applicationName: it.applicationName,
                displayName: it.displayName,
                type: it.class.name,
                isRoot: !_ctx.parent
            ]
        } as Closure<Map<String,?>>

        def context = contextProps(_ctx)
        // _ctx.parent?.with {context.parent = contextProps(it)}
        _ctx.parent?.with {context.parentContextId = contextQualifier(it)}

        resp[key] = context

        def bf = _ctx.beanFactory as DefaultListableBeanFactory
        def beanDefinitions = bf.beanDefinitionNames.collectEntries { [(it): bf.getBeanDefinition(it)] } as Map<String, BeanDefinition>

        context.beanDefinitions = []

        def searchType = {BeanDefinition bd ->
            while (bd) {
                if (bd.beanClassName) return bd.beanClassName
                bd = bd.parentName && bf.containsBeanDefinition(bd.parentName) ? bf.getBeanDefinition(bd.parentName) : null
            }
            return null
        }

        if (dumpBeans) {

            beanDefinitions.sort{it.key}.eachWithIndex { k, v, ix ->

                def beanDefinition = [
                        name: k,
                        beanDefinitionType: v.getClass().name,
                        type: searchType(v),
                        abstract: v.isAbstract(),
                        parent: v.parentName,
                        scope: v.scope,
                        singleton: v.isSingleton(),
                        prototype: v.isPrototype(),
                        path: v.resourceDescription
                ] as Map<String,?>

                _ctx.getAliases(k)?.with{ if (it) beanDefinition.aliases = it }

                context.beanDefinitions << beanDefinition
                context.aliases = FieldUtils.readField(bf, 'aliasMap', true)

            }

        }

        _ctx = _ctx.parent
        root = false

    }

}

try {

    dumpSpringContext(spring.parent.parent, true)

} catch (any) {
    println "${any}\n  ${any.stackTrace.join('\n  ')}"
    throw any
}

JsonOutput.toJson(resp)