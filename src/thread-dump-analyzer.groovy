@Grapes(
        @Grab(group='com.github.mpkorstanje', module='simmetrics-core', version='4.1.0')
)

import org.simmetrics.StringMetric
import org.simmetrics.metrics.StringMetrics

StringMetric metric = StringMetrics.levenshtein()

CliBuilder cli = new CliBuilder(usage: 'thread-dump-analyzer [options] <thread dump>')
cli.h(longOpt: 'help', 'Print this help')
cli.t(longOpt: 'threshold', args: 1, 'If the result of similarity match is higher than the threshold, thread names are considered as same. Valid values are real numbers between 0 and 1. Default is 0.8.')

def options = cli.parse(args)

if (options.getProperty('h')) {
    cli.usage()
    System.exit(0)
}

if (options.arguments().size() != 1) {
    cli.usage()
    System.exit(1)
}

def threshold = options.getProperty('t') ? Float.parseFloat(options.getProperty('t')) : 0.8

File threadDump = new File(options.arguments().get(0))

def threadNameRegex = /^"(.*)".*$/
def groups = []

threadDump.eachLine { line ->
    def match = line =~ threadNameRegex
    if (match) {
        def threadName = match[0][1]
        def found = false
        for (def group : groups) {
            def groupName = group[0]
            if (metric.compare(groupName, threadName) > threshold) {
                group[1]++
                found = true
                break
            }
        }
        if (!found) {
            groups.add([ threadName, 1 ])
        }
    }
}

groups = groups.sort({ a,b -> a[1] <=> b[1] }).reverse()

def sum = 0
for (def group: groups) {
    println "${group[0]}: ${group[1]}"
    sum += group[1]
}

println()
println "Sum: $sum"