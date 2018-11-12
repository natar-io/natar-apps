#!/bin/sh

prog=target/nectar-configuration-loader-0.1-SNAPSHOT-jar-with-dependencies.jar
data=$SKETCHBOOK/libraries/PapARt/data/calibration

## TODO: read arguments.
host="oj.lity.tech"
port="6389"

hostport="-rh "$host" -rp "$port

#RGB Camera
java -jar $prog -p / -f $data/calibration-AstraS-rgb.yaml -pd -o camera0:calibration $hostport

#Depth Camera
java -jar $prog -p / -f $data/calibration-AstraS-depth.yaml -o camera0:calibration:depth -pd $hostport
java -jar $prog -p / -f $data/calibration-AstraS-stereo.xml -o camera0:extrinsics:depth -m $hostport

#Projector
java -jar $prog -p / -f $data/projector.yaml -o projector0:calibration -pd -pr $hostport
java -jar $prog -p / -f $data/camProjExtrinsics.xml -o projector0:extrinsics -m -i $hostport

#Table
java -jar $prog -p / -f $data/tablePosition.xml -m -o table:extrinsics $hostport
