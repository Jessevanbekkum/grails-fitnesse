import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH

import nl.jworks.grails.plugin.fitnesse.FitnesseFixtureArtefactHandler
import nl.jworks.grails.plugin.fitnesse.GrailsFitnesseSlimServer
import nl.jworks.grails.plugin.fitnesse.GrailsSlimFactory
import nl.jworks.groovy.ClosureMetaMethodWithReturnType
import nl.jworks.grails.plugin.fitnesse.DefaultGrailsFitnesseFixtureClass
import org.slf4j.LoggerFactory
import org.slf4j.Logger

class FitnesseGrailsPlugin {
    // Plugin defaults
    public static final int DEFAULT_SERVER_PORT = 8085
    public static final boolean DEFAULT_VERBOSITY = false
    private final Logger log = LoggerFactory.getLogger("nl.jworks.grails.plugin.fitnesse.FitnesseGrailsPlugin")

    // the plugin version
    def version = "0.5"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.2 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    def scopes = CH.config.grails.plugin.fitnesse.disabled ? [excludes: 'all'] : []

    def author = "Erik Pragt"
    def authorEmail = "erik.pragt@jworks.nl"
    def title = "Fitnesse Grails Plugin"
    def description = '''The Fitnesse Grails plugin provides a bridge between Fitnesse and Grails.'''
    def documentation = "http://bodiam.github.com/grails-fitnesse-plugin/docs/manual/"

    def artefacts = [ FitnesseFixtureArtefactHandler ]

    def watchedResources = [
            "file:./grails-app/fitnesse/**/*.groovy",
            "file:../../plugins/*/grails-app/fitnesse/**/*.groovy"
    ]

    def doWithWebDescriptor = { xml ->

    }

    def doWithSpring = {
        def startPort = CH.config.grails.plugin.fitnesse.slim.port ?: DEFAULT_SERVER_PORT
        def verbose = CH.config.grails.plugin.fitnesse.slim.verbose ?: DEFAULT_VERBOSITY

        grailsFitnesseSlimServer(GrailsFitnesseSlimServer, startPort, verbose) {
            grailsSlimFactory = ref('grailsSlimFactory')
        }
        grailsSlimFactory(GrailsSlimFactory) {
            sessionFactory = ref('sessionFactory')
        }

        final beanConfigureClosure = configureFixtureBean.clone()
        beanConfigureClosure.delegate = delegate

        application.fitnesseFixtureClasses.each { fixtureClass ->
            beanConfigureClosure.call(fixtureClass)
        }
    }

    def doWithDynamicMethods = { ctx ->
        application.fitnesseFixtureClasses.each {
            addFixtureDynamicMethods(it)
        }
    }

    def doWithApplicationContext = { applicationContext ->
    }

    def onChange = { event ->
        if (application.isFitnesseFixtureClass(event.source)) {
            application.addArtefact(FitnesseFixtureArtefactHandler.TYPE, event.source)
        }
        application.fitnesseFixtureClasses.each { DefaultGrailsFitnesseFixtureClass fixtureClass ->
            Class underlyingClass = fixtureClass.clazz
            try {
                Class reloadedClass = application.classLoader.reloadClass(underlyingClass.name)
                DefaultGrailsFitnesseFixtureClass reloadedFixtureClass = new DefaultGrailsFitnesseFixtureClass(reloadedClass)
                final beanConfigureClosure = configureFixtureBean.clone()
                beans {
                    beanConfigureClosure.delegate = delegate
                    beanConfigureClosure.call(reloadedFixtureClass)
                }.registerBeans(event.ctx)
                addFixtureDynamicMethods(reloadedFixtureClass)
            } catch (Throwable th) {
                log.warn("Class $underlyingClass.name wasn't reloaded because an exception occured", th)
            }
        }
        log.debug("Reloaded ${application.fitnesseFixtureClasses.size()} fixture classes")
    }

    def onConfigChange = { event ->

    }

    def addFixtureDynamicMethods = { fixtureClass ->
        if (fixtureClass.isQueryFixture) {
            fixtureClass.metaClass.mapResults = { Collection results, def propertyMapping ->
                if (propertyMapping in Collection) {
                    propertyMapping = propertyMapping.inject([:]) { map, property ->
                        map << new MapEntry(property, property)
                    }   
                }

                results.collect { result ->
                    propertyMapping.collect { name, property ->
                        [name, result[property].toString()]
                    }   
                }
            }
        }

        if (fixtureClass.mapping) {
            registerClosureMetaMethodWithReturnType(fixtureClass.metaClass, 'query', List) { ->
                mapResults(queryResults(), mapping)
            }
        }
    }

    def configureFixtureBean = { fixtureClass ->
        "${fixtureClass.propertyName}"(fixtureClass.clazz) { bean ->
            bean.singleton = false
            bean.autowire = 'byName'
        }
    }

    private void registerClosureMetaMethodWithReturnType(MetaClass ownerMetaClass, String name, Class returnType, Closure closure) {
        ClosureMetaMethodWithReturnType metaMethod = new ClosureMetaMethodWithReturnType(name, returnType, this.class, closure)
        if (!(ownerMetaClass in ExpandoMetaClass)) {
            ownerMetaClass.replaceDelegate()
        }
        ownerMetaClass.registerInstanceMethod(metaMethod)
    }
}
