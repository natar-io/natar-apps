#!/bin/sh

prog=target/nectar-configuration-loader-0.1-SNAPSHOT-jar-with-dependencies.jar

java -jar $prog -f data/calibration-AstraS-depth.yaml -o "calibration:camera:astraS-depth" -pd
java -jar $prog -f data/calibration-AstraS-rgb.yaml -o "calibration:camera:astraS-rgb" -pd

java -jar $prog -f data/camProjExtrinsics.xml -m -o "calibration:extrinsics:camera#0:projector#0"
