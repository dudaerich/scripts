@Grapes(
        @Grab(group='commons-io', module='commons-io', version='2.5')
)

import groovy.io.FileType
import org.apache.commons.io.FilenameUtils

// private functions

class Helpers {

    private static def packageRegex = /^package (.+);$/

    public static String parsePackage(File file) {
        BufferedReader reader = new BufferedReader(new FileReader(file))
        String line

        while ((line = reader.readLine()) != null) {
            def test = line =~ packageRegex
            if (test) {
                return test[0][1]
            }
        }
        return ""
    }

}

CliBuilder cli = new CliBuilder(usage: 'used-classes [options] <project dir> [<out file>]')

def options = cli.parse(args)

if (options.arguments().size() == 1) {
    File projectDir = new File(options.arguments().get(0))

    def seenClasses = new HashSet()

    projectDir.traverse type: FileType.FILES, nameFilter: ~/.*\.java/, { file ->
        def className = FilenameUtils.removeExtension(file.getName())
        def packageName = Helpers.parsePackage(file)

        if (seenClasses.contains(className)) {
            return
        }

        seenClasses.add(className)
        println "RULE usage of class $packageName.$className"
        println "CLASS $packageName.$className"
        println "METHOD <init>"
        println "AT ENTRY"
        println "IF true"
        println "DO traceln(\"BYTEMAN \" + \$0.getClass().getName())"
        println "ENDRULE"
        println()
    }
} else if (options.arguments().size() == 2) {
    File projectDir = new File(options.arguments().get(0))
    File outFile = new File(options.arguments().get(1))

    def seenFiles = new HashSet()
    projectDir.traverse type: FileType.FILES, nameFilter: ~/.*\.java/, { seenFiles.add(it.getAbsolutePath()) }

    def matcher = /^BYTEMAN (.+)$/
    def classes = new HashSet()
    outFile.eachLine { line ->
        def test = line =~ matcher
        if (test) {
            classes.add(test[0][1])
        }
    }
    classes.each { className ->
        className = className.replace('.', '/') + ".java"
        seenFiles.each {
            if (it.endsWith(className)) {
                String relativePath = projectDir.toURI().relativize(new File(it).toURI()).getPath()
                println relativePath
            }
        }
    }

} else {
    cli.usage()
    System.exit(1)
}