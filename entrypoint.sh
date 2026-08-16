#!/bin/bash

# Use sudo, because we need privileged port
echo "Starting Go DNS..."
sudo /home/fargonespy/dns --answer_ip ${ANSWER_IP} --forwarder ${FORWARD_IP} &
DNS_PID="$!"

sleep 2s

echo "Starting FarGoneSpy..."
sudo java -jar /home/fargonespy/fargonespy.jar

sudo kill "${DNS_PID}"
