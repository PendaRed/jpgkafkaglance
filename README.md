# KafkaGlance

April 2018: a very simple Kafka monitor to display topics and offsets and lags.

#### @TODO
- If socket already listened to log and fail fast.
- Find someplace to hold the release tarball.

## How to run it?

You must configure it using application.conf - as it is an Akka application.

-Dconfig.file=/path/to/application.conf
-Dlogback.configurationFile=/path/to/config.xml

You should also set up logback, see below for an example logback.xml.

## Why?

The new KafkaAdminClient API offers a simple way to obtain consumer group and topic partition information.

While monitoring services it is great to see at-a-glance which topics are actively being read from and by how many processes.

ie:

| Topic Name | Consumer Group | Num Consumers | Lag | Offset | End Pos |
|------------|----------------|---------------|-----|--------|---------|
| ATopic     | MyConsumer1    |             3 |    2|    200 |     202 |

## Versions?

Its Scala 2.12 compatible with Kafka 1.0.

## Functionality

KafkaGlance is for simple at a glance monitoring.  It is not suitable for hundreds of nodes running thousands of topics.
If you do that you have your own teams to write devops tools.

## Running on linux

The files to run it on linux are all in Git, under src/main/linux

## Building the assembly
To build it use:
sbt clean assembly

Then scp the jar up, along with the files in the linux dir and it will run.
I'll upload a release tarball at some point.

It will place the assembly somewhere like:
C:\dev\jpgkafkaglance\kafkaglance\target\scala-2.12\kafka-glance.jar
