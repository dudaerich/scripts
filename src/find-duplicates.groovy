import groovy.json.JsonSlurper

class Message {

    String messageId;
    String dupId;

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        Message message = (Message) o

        if (dupId != message.dupId) return false

        return true
    }

    int hashCode() {
        return (dupId != null ? dupId.hashCode() : 0)
    }


    @Override
    public String toString() {
        return "Message{" +
                "messageId='" + messageId + '\'' +
                ", dupId='" + dupId + '\'' +
                '}';
    }
}

CliBuilder cli = new CliBuilder(usage: 'find-duplicates <file>')
cli.h(longOpt: 'help', 'Print this help');

def options = cli.parse(args)

if (options.arguments().getProperties().get('h')) {
    cli.usage()
    System.exit(0)
}

if (options.arguments().size() != 1) {
    cli.usage()
    System.exit(1)
}

File input = new File(options.arguments().get(0))

def jsonSlurper = new JsonSlurper()
def json = jsonSlurper.parse(input)

Set<Message> uniques = new HashSet()

for (def msg : json) {
    Message message = new Message(messageId: msg.JMSMessageID, dupId: msg."_AMQ_DUPL_ID")
    if (!uniques.add(message)) {
        println "Duplication deteceted: $message"
    }

}