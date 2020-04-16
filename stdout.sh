#!/bin/bash

scala \
    -cp build/libs/session-generator-0.1-all.jar \
    com.github.sessiongen.generator.Generator \
    --rate 1000 \
    --interval 60 \
    --logout-probability 0.2 \
    --payload-size 20 \
    --running-time 3600 \
    --output stdout

#    --output kafka \
#    --producer-property-file src/main/resources/producer.properties \
#    --topic dacoe-heartbeat
