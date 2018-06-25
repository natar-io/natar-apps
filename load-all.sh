#!/bin/sh

prog=target/nectar-configuration-loader-0.1-SNAPSHOT-jar-with-dependencies.jar

java -jar $prog -f data/calibration-AstraS-depth.yaml -o "calibration:camera:astraS-depth" -pd
java -jar $prog -f data/calibration-AstraS-rgb.yaml -pd -o camera0:calibration
java -jar $prog -f data/projector.yaml -pd -pr -o projector0:calibration
java -jar $prog -f data/camProjExtrinsics.xml -m -o projector0:extrinsics
