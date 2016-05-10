@Grapes(
        @Grab(group='commons-io', module='commons-io', version='2.5')
)


import groovy.io.FileType
import groovy.text.SimpleTemplateEngine
import org.apache.commons.io.FilenameUtils

def cli = new CliBuilder(usage: 'groovy projectFromTemplate.groovy <dir> <count>')
cli.h(longOpt: 'help', 'Print this help')

def options = cli.parse(args)

if (options.arguments().getProperties().get('h')) {
    cli.usage()
    System.exit(0)
}

if (options.arguments().size() != 2) {
    cli.usage()
    System.exit(1)
}

File dir = new File(options.arguments().get(0))
int count = options.arguments().get(1).toInteger()

def engine = new SimpleTemplateEngine()

dir.traverse type: FileType.FILES, nameFilter: ~/.*\.template/, { file ->
    def template = engine.createTemplate(file)
    for (int i = 0; i < count; i++) {
        String fileName = FilenameUtils.removeExtension(file.getName())
        fileName = "${FilenameUtils.removeExtension(fileName)}$i.${FilenameUtils.getExtension(fileName)}"
        File out = new File(file.getParentFile(), fileName)
        template.make([i: i]).writeTo(new FileWriter(out))
    }
}