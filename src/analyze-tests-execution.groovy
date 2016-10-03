import groovy.json.JsonSlurper
import org.junit.Ignore
import org.junit.Test
import org.reflections.Reflections
import org.reflections.scanners.MethodAnnotationsScanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.util.ConfigurationBuilder

import java.util.concurrent.atomic.AtomicInteger

@Grab(group='org.reflections', module='reflections', version='0.9.10')
@Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.21')
@Grab(group='dom4j', module='dom4j', version='1.6.1')

class ProgressBar {

    static Thread thisThread
    static Thread monitoringThread
    static int progressMax
    static AtomicInteger progressActual
    static String description

    static void reset() {
        thisThread = null
        monitoringThread = null
        progressActual = new AtomicInteger(1)
        description = null
    }

    static void start() {
        thisThread = new Thread({
            String anim= "|/-\\";

            int animX = 0;

            while (monitoringThread.isAlive()) {
                double progress = progressActual.get() * 1.0 / progressMax * 100
                String data = "\r" + description + " - " + anim.charAt(animX % anim.length())  + " " + Math.round(progress) + " %" ;
                System.out.write(data.getBytes());
                Thread.sleep(100);
                animX++;
                if (animX > anim.length()) {
                    animX = 0;
                }
            }
            System.out.write("\r")
        })
        thisThread.start()
    }

    static void progress() {
        progressActual.incrementAndGet()
    }

    static void join() {
        if (thisThread) {
            thisThread.join()
        }
    }

}

def getClassPathURLs =  { mavenProject ->
    List<URL> result = new ArrayList<>()
    mavenProject.traverse {
        if (it.isDirectory() && ("classes".equals(it.name) || "test-classes".equals(it.name))) {
            result.add(it.toURI().toURL())
        }
    }
    return result
}

def countLines = { file ->
    LineNumberReader lineNumberReader = new LineNumberReader(new FileReader(file))
    lineNumberReader.skip(Long.MAX_VALUE)
    def count = lineNumberReader.getLineNumber() + 1
    lineNumberReader.close()
    return count
}

CliBuilder cli = new CliBuilder(usage: 'analyze-tests-execution <maven-project> <jenkins-view>')
cli.h(longOpt: 'help', 'Print this help')

def options = cli.parse(args)

if (options.getProperty('h')) {
    cli.usage()
    System.exit(0)
}

if (options.arguments().size() != 2) {
    cli.usage()
    System.exit(1)
}

File mavenProject = new File(options.arguments().get(0))
URL jenkinsView = new URL(options.arguments().get(1))
String jobsLogFileName = "jobs.log"

assert mavenProject.exists()
assert mavenProject.isDirectory()

Set<String> testClasses = new HashSet<>()

Set<String> ignoredTestClasses = new HashSet<>()
Set<String> ignoredTestMethods = new HashSet<>()

def exceptions = []

def processMavenProject = {
    Reflections reflections = new Reflections(new ConfigurationBuilder().setUrls(getClassPathURLs(mavenProject)).setScanners(new MethodAnnotationsScanner(), new TypeAnnotationsScanner(),  new SubTypesScanner()))

    def testMethods = reflections.getStore().get(MethodAnnotationsScanner.class.getSimpleName()).get(Test.class.getName())

    ignoredTestClasses.addAll(reflections.getStore().get(TypeAnnotationsScanner.class.getSimpleName()).get(Ignore.class.getName()))
    ignoredTestMethods.addAll(reflections.getStore().get(MethodAnnotationsScanner.class.getSimpleName()).get(Ignore.class.getName()))

    def subTypes = reflections.getStore().get(SubTypesScanner.class.getSimpleName())

    for (String testMethod : testMethods) {
        testClasses.add(testMethod.substring(0, testMethod.lastIndexOf(".")))
    }

    def newClassesFounded = true

    while (newClassesFounded) {
        newClassesFounded = false
        for (String clazz : subTypes.keySet()) {
            if (testClasses.contains(clazz)) {
                for (def c : subTypes.get(clazz)) {
                    if (!testClasses.contains(c)) {
                        testClasses.add(c)
                        newClassesFounded = true
                    }
                }
            }
        }
    }
}

def processJenkinsView = {
    File jobsLogFile = new File(jobsLogFileName)
    if (jobsLogFile.exists()) {
        return
    }
    def jsonSlurper = new JsonSlurper()
    def viewJson = jsonSlurper.parse(new URL("${jenkinsView.toExternalForm()}/api/json"))

    ProgressBar.reset()
    ProgressBar.monitoringThread = Thread.currentThread()
    ProgressBar.progressMax = viewJson.jobs.size()
    ProgressBar.description = "Downloading logs"
    ProgressBar.start()

    FileOutputStream outputStream = new FileOutputStream(jobsLogFile, true)

    for (def job : viewJson.jobs) {
        try {
            def jobJson = jsonSlurper.parse(new URL("${job.url}/lastCompletedBuild/api/json?tree=runs[url]"))

            if (jobJson.runs) {
                for (def run : jobJson.runs) {
                    URL runUrl = new URL(run.url + "/consoleText")
                    def connection = runUrl.openConnection()
                    outputStream << connection.getInputStream()
                }
            } else {
                URL jobUrl = new URL("${job.url}/lastCompletedBuild/consoleText")
                def connection = jobUrl.openConnection()
                outputStream << connection.getInputStream()
            }
        } catch (RuntimeException e) {
            exceptions.add([job: job.name, exception: e])
        } finally {
            ProgressBar.progress()
        }
    }

    outputStream.close()
}

Thread processMavenProjectThread = new Thread(processMavenProject)
Thread processJenkinsViewThread = new Thread(processJenkinsView)

processMavenProjectThread.start()
processJenkinsViewThread.start()

processMavenProjectThread.join()
processJenkinsViewThread.join()
ProgressBar.join()

File jobsLogFile = new File(jobsLogFileName)

ProgressBar.reset()
ProgressBar.progressMax = countLines(jobsLogFile)
ProgressBar.description = "Searching test classes in logs"

def searchTestClasses = {
    ProgressBar.monitoringThread = Thread.currentThread()
    ProgressBar.start()
    jobsLogFile.eachLine { line ->
        def i = testClasses.iterator()
        while (i.hasNext()) {
            if (line.contains(i.next())) {
                i.remove()
            }
        }
        ProgressBar.progress()
    }
}


Thread searchTestClassesThread = new Thread(searchTestClasses)

searchTestClassesThread.start()

searchTestClassesThread.join()
ProgressBar.join()

if (!exceptions.isEmpty()) {
    println "Exceptions raised during downloading of logs:"
    for (def e : exceptions) {
        println "  ${e.job}: ${e.exception}"
    }
    println ""
}

if (!ignoredTestClasses.isEmpty()) {
    println "Ignored test classes:"
    for (def testClass : ignoredTestClasses.toSorted()) {
        println "  $testClass"
    }
    println ""
}

if (!ignoredTestMethods.isEmpty()) {
    println "Ignored test methods:"
    for (def testMethod : ignoredTestMethods.toSorted()) {
        println "  $testMethod"
    }
    println ""
}

if (testClasses.isEmpty()) {
    println "All test classes were found in logs."
} else {
    println "Test classes not found in log"
    for (def testClass : testClasses.toSorted()) {
        println "  $testClass"
    }
}