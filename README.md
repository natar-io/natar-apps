## Nectar Pose estimator.


For now it loads configurations into redis for tests.

## How to use

1. Build the program.

`mvn package`

2. Load parameters

``` bash

java -jar target/nectar-configuration-loader-0.1-SNAPSHOT-jar-with-dependencies.jar -f data/calibration-AstraS-rgb.yaml -pd -o camera0:calibration
java -jar target/nectar-configuration-loader-0.1-SNAPSHOT-jar-with-dependencies.jar -f data/projector.yaml -pd -pr -o projector0:calibration

java -jar target/nectar-configuration-loader-0.1-SNAPSHOT-jar-with-dependencies.jar -f data/camProjExtrinsics.xml -m -o projector0:extrinsics

java -jar target/nectar-configuration-loader-0.1-SNAPSHOT-jar-with-dependencies.jar -f data/tablePosition.xml -m -o scene:tablePosition


```