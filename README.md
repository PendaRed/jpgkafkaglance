<img src="https://pendared.github.io/jpgkafkaglance/images/kglance.png">

April 2018: a very simple Kafka monitor to display topics and offsets and lags.

Not a replacement for the Yahoo Kafka Monitor, more a devops util for smallish Kafka installs.

## Download the release

<table>
  <tr>
    <th>Compatibility</th>
    <th>Date</th>
    <th>Version</th>
    <th>Link</th>
    <th>Md5 checsum</th>
  </tr><tr>
    <td>Kafka 1.0</td>
    <td>April 2018</td>
    <td>1.0.4</td>
    <td><a href="https://pendared.github.io/jpgkafkaglance/releases/kafka-glance_2.12-1.0.4.tar.gz">kafka-glance_2.12-1.0.4.tar.gz</a></td>
    <td><a href="https://pendared.github.io/jpgkafkaglance/releases/kafka-glance_2.12-1.0.4.tar.gz.md5">kafka-glance_2.12-1.0.4.tar.gz.md5</a></td>
  </tr>
</table>

### Validate the release

```
md5sum -c kafka-glance_2.12-1.0.4.tar.gz.md5
kafka-glance_2.12-1.0.4.tar.gz: OK
```

## How to run it?

Configure it by editing the config/application.conf for your own webhost and also the kafka broker details.

There is a README which tells you what to do.
```
./kafka-glance.sh start
./kafka-glance.sh stop
./kafka-glance.sh restart
```

OR you can do it all yourself from source, and then upload the start script, the heart of which is:

```
${java_cmd} -cp "${SCRIPTDIR}/config/:kafka-glance.jar" com.jgibbons.kglance.KafkaGlance -Dlogback.configurationFile=${SCRIPTDIR}/config/logback.xml > logs/kafka-glance.stdout 2>&1 & echo $!> ${PIDFILE}
```

Look in src/main/linux for some example configs and scripts

## Why?

The new KafkaAdminClient API offers a simple way to obtain consumer group and topic partition information.

While monitoring services it is great to see at-a-glance which topics are actively being read from and by how many processes.

ie:

<img src="https://pendared.github.io/jpgkafkaglance/images/kglance_topics_v1.0.0.png">

## Versions?

Its Scala 2.12 compatible with Kafka 1.0.

## Functionality

KafkaGlance is for simple at a glance monitoring.  It is not suitable for hundreds of nodes running thousands of topics.
If you do that you have your own teams to write devops tools.

#Building it yourself
No need for this, download the tar.gz above, but if you want to, then ...

## Building the assembly
To build it use:
sbt clean assembly

Then scp the jar up, along with the files in the linux dir and it will run.

It will place the assembly somewhere like:
C:\dev\jpgkafkaglance\kafkaglance\target\scala-2.12\kafka-glance.jar

## Creating the tarball

Upload to your linux server, into a versioned dir such as kafka-glance_2.12-1.0.0

```
tar -cvf kafka-glance_2.12-1.0.4.tar kafka-glance_2.12-1.0.4
gzip kafka-glance_2.12-1.0.4.tar

md5sum kafka-glance_2.12-1.0.4.tar.gz > kafka-glance_2.12-1.0.4.tar.gz.md5
```

## Running on linux

The files to run it on linux are all in Git, under src/main/linux, or download the release above.
