# See LICENSE for license details.

# Directories
ROOT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
SRC_DIR  := $(ROOT_DIR)/src/main/scala

SBT := java -jar $(ROOT_DIR)/../rocket-chip/sbt-launch.jar

.PHONY: build.sbt clean

AES_SCALA      = ${SRC_DIR}/AES128Opt.scala ${SRC_DIR}/AES128OptCore.scala
CWMAC_SCALA    = ${SRC_DIR}/CWMACOpt.scala ${AES_SCALA}
TREE_SCALA     = ${SRC_DIR}/Tree.scala ${CWMAC_SCALA} ${AES_SCALA}
BACKEND_SCALA  = ${SRC_DIR}/Backend.scala ${SRC_DIR}/FCFSArbiter.scala
FRONTEND_SCALA = ${SRC_DIR}/Frontend.scala
MPE_SCALA      = ${SRC_DIR}/MPE.scala ${TREE_SCALA} ${BACKEND_SCALA} ${FRONTEND_SCALA}

build.sbt:
	ln -sf ${ROOT_DIR}/build.sbt.bak ${ROOT_DIR}/build.sbt

AES128Opt.v: build.sbt ${AES_SCALA}
	java -jar ../rocket-chip/sbt-launch.jar "runMain nvsit.AES128OptDriver"

CWMACOpt.v: build.sbt ${CWMAC_SCALA}
	java -jar ../rocket-chip/sbt-launch.jar "runMain nvsit.CWMACOptDriver"

Tree.v: build.sbt ${TREE_SCALA}
	java -jar ../rocket-chip/sbt-launch.jar "runMain nvsit.TreeDriver"

MPE.v: build.sbt ${MPE_SCALA}
	java -jar ../rocket-chip/sbt-launch.jar "runMain nvsit.MPEDriver"

clean:
	rm -rf *.fir *.anno.json *.f *.v *.log project target
	make -C xsim clean
