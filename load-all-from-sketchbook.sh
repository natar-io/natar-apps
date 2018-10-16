#!/bin/sh

prog=target/nectar-configuration-loader-0.1-SNAPSHOT-jar-with-dependencies.jar
data=$SKETCHBOOK/libraries/PapARt/data/calibration

#RGB Camera
java -jar $prog -p / -f $data/calibration-AstraS-rgb.yaml -pd -o camera0:calibration

#Depth Camera
java -jar $prog -p / -f $data/calibration-AstraS-depth.yaml -o camera0:calibration:depth -pd
java -jar $prog -p / -f $data/calibration-AstraS-stereo.xml -o camera0:extrinsics:depth -m

#Projector
java -jar $prog -p / -f $data/projector.yaml -o projector0:calibration -pd -pr
java -jar $prog -p / -f $data/camProjExtrinsics.xml -o projector0:extrinsics -m -i

#Table
java -jar $prog -p / -f $data/tablePosition.xml -m -o table:extrinsics
