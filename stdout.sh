#!/bin/bash

scala \
    -cp build/libs/session-generator-1.0-SNAPSHOT-all.jar \
    com.github.sessiongen.Generator \
    --rate 1000 \
    --interval 60 \
    --total 600000 \
    --logout-probability 0.2 \
    --payload-size 20 \
    --running-time 3600 \
    --output stdout

#    --output kafka \
#    --producer-property-file src/main/resources/producer.properties \
#    --topic dacoe-heartbeat
