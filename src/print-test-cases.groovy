import groovy.io.FileType

File dataDir = new File(args[0])

dataDir.traverse(
        type        : FileType.FILES,
        nameFilter  : ~/.*\.xml/
) {
    def xml = new XmlSlurper().parse(it)
    xml.'*'.findAll {
        node -> node.name() == 'testcase'
    }.each {testcase -> println "${testcase.'@classname'}.${testcase.'@name'}" }
}