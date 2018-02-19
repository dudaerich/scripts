import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter

@Grab(group='org.apache.commons', module='commons-csv', version='1.4')

class Tools {

    static Map<String, String> loadVersions(File file) {
        def pom = new XmlParser().parse(file)

        if (!pom?.properties) {
            throw new Exception("The pom ${file.path} do not contain project/properties element")
        }

        def versions = pom.properties.'*'.findAll { it.name().localPart.startsWith('version.') }

        return versions.collectEntries {
            String artifact = it.name().localPart.replaceAll(/^version\./, '')
            String version = it.value()[0].replaceAll(/.redhat-.*$/, '')
            version = version.replaceAll(/jbossorg-/, '')
            [artifact, version]
        }
    }

}

File oldPom = new File(args[0])
File newPom = new File(args[1])

Map oldVersions = Tools.loadVersions(oldPom)
Map newVersions = Tools.loadVersions(newPom)

File comparisionFile = new File("comparision.csv")
File deletedFile = new File("deleted.csv")
File addedFile = new File("added.csv")

comparisionFile.withWriter { comparisionWriter ->
    deletedFile.withWriter { deletedWriter ->

        CSVPrinter comparisionPrinter = CSVFormat.EXCEL.withHeader('artifact', 'old', 'new').print(comparisionWriter)
        CSVPrinter deletedPrinter = CSVFormat.EXCEL.withHeader('artifact', 'version').print(deletedWriter)

        oldVersions.each { k, v ->
            if (!newVersions.containsKey(k)) {
                deletedPrinter.printRecord(k, v)
            } else {
                String oldVersion = v
                String newVersion = newVersions.get(k)
                comparisionPrinter.printRecord(k, oldVersion, newVersion)
            }
        }
    }
}

addedFile.withWriter { addedWriter ->
    CSVPrinter addedPrinter = CSVFormat.EXCEL.withHeader('artifact', 'version').print(addedWriter)

    newVersions.each { k, v ->
        if (!oldVersions.containsKey(k)) {
            addedPrinter.printRecord(k, v)
        }
    }
}