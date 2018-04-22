#!/bin/bash
usage() {
  echo "Usage: kafka-glance.sh start|restart|stop|force-stop|force-restart}" >&2
}

# At least one argument is required.
if [[ -z "${1}" ]]; then
  usage
  exit 1
fi

SCRIPTDIR=$(dirname $0)
PIDFILE="${SCRIPTDIR}/kafka-glance.pid"

# mk logdir if need be
if [ ! -d "${SCRIPTDIR}/logs" ]; then
  mkdir ${SCRIPTDIR}/logs
fi

# Get the PID from PIDFILE if we don't have one yet.
if [[ -z "${PID}" && -e ${PIDFILE} ]]; then
  PID=$(cat ${PIDFILE});
fi

# Detect if we should use JAVA_HOME or just try PATH.
get_java_cmd() {
  if [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]];  then
    echo "$JAVA_HOME/bin/java"
  else
    echo "java"
  fi
}

start() {
  echo "Starting kafka-glance (PID written to $PIDFILE)."
  java_cmd=$(get_java_cmd)

  nohup ${java_cmd} -cp "${SCRIPTDIR}/config/:kafka-glance.jar" com.jgibbons.kglance.KafkaGlance -Dlogback.configurationFile=${SCRIPTDIR}/config/logback.xml > logs/kafka-glance.stdout 2>&1 & echo $!> ${PIDFILE}
}

stop() {
  if [[ -z "${PID}" ]]; then
    echo "kafka-glance is not running (missing PID)."
  else
    kill $1 ${PID}
  fi
}

case "$1" in
  start)
        start;
        ;;
  restart)
        stop; sleep 1; start;
        ;;
  stop)
        stop
        ;;
  force-stop)
        stop -9
        ;;
  force-restart)
        stop -9; sleep 1; start;
        ;;
  *)
        usage
        exit 4
        ;;
esac

exit 0


