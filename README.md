# OpenMPE
This repository contains source files for MPE (Memory Protection Engine).
All sources are open-sourced under Apache 2.0 license.

**OpenMPE** is an open-source Memory Protection Engine (MPE) based on Intel SGX-style Integrity Tree (SIT).
Details of SIT are explained in [Intel SGX whitepaper (Intel SGX Explained)](https://eprint.iacr.org/2016/086.pdf).

As of v1.0.0, some of modules of the OpenMPE is designed/optimized for the modified Freedom SoC.
If you want to OpenMPE "NOT AS-IS", some modifications may be required.

# Requirements
- This repository was created and tested under the following repository/versions.
- Chisel
  - 2.12.10
- Rocket-Chip
  - https://github.com/uyiromo/rocket-chip/tree/f8d282faad70853be59ae005ca7de562cba05dad
  - This commit is an extension of the `Rocket-Chip 27120ee42 (Wed Jan 22 15:57:07 2020 -0800)`
- Freedom SoC
  - https://github.com/uyiromo/freedom/commit/d1c3505bff30bb1864517f2f3a58c1e510ab8193

# How to use
## Instantiate
- Clone this repository
- Add this to your `build.sbt`
```sbt
// Example of Freedom SoC
lazy val commonSettings = Seq(
  scalaVersion := "2.12.4",  // This needs to match rocket-chip's scalaVersion
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xsource:2.11",
    "-language:reflectiveCalls"
  ),
  autoCompilerPlugins := true
)

lazy val rocketChip = RootProject(file("rocket-chip"))

lazy val openmpe = (project in file("OpenMPE")).
  dependsOn(rocketChip).
  settings(commonSettings: _*).
  settings(addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full))
```

- Second, incluse and instantiate
  - Scala file
  - For I/O connection, please refer the `I/O` section.
```scala
import openmpe._

val mpe = Module(new MPE(8))

// Some of you may need withClockAndReset like this:
val mpe = withClockAndReset(mpeClk, mpeRst){ Module(new MPE(8)) }
```

## Parameters
- OpenMPE uses SGX1 design
  - 8-ary, 4-level Tree. In other words, ONE SIT can cover 256 KiB.
  - 384 SIT roots
  - 256 KiB/root * 384 roots = 96 MiB. with 32 MiB metadata.
- We have a plan to make the OpenMPE more parametarized.

### Module Design
- `numTree`
  - The number of SIT modules.
    - CPU can issue read/writes to the protected memory PARALLELY at the maximum of `numTree`  
  - You can specify this when instantiating.
    - `Module(new MPE(8))`
  - Allowed Values
    - NO LIMIT. You can specify as your hardware allows.
    - ***MUST BE A POWER OF TWO.***

### Tree Behavior
- `numRoot`
  - The number of SIT roots.
    - Covered Memory Size: 256 KiB * `numRoot`.
    - 384 for SGX1 (96 MiB)
    - 768 for SGX2 (192 MiB)
  - Defined in `src/main/scala/Frontend.scala`
  - Allowed Values
    - NO LIMIT. 
    - When you set this bigger than 384, please adjust address map.
  - We write the module to make Chisel infer BRAM mapping automatically.
    - As far as we tested, Xilinx Vivado maps the root pool.  

## Memory Map
- Defined in `src/main/scala/AddressMap.scala`
  - `PM_BEGIN`: the beggining address of PM (Protected Memory)
  - `PM_END`: the end address of PM
    - When you set `numRoot` bigger than 384, change this value 
- Assumption & Optimization:
  - All memory accesses are issued in cacheline size (64-byte).
  - Omit lower 6-bits in addresses.

```scala
object AddressMap {
  val DRAM_BEGIN = 0x0000000L.U(26.W)
  val DRAM_END   = 0x0FFFFFFL.U(26.W)
  val NVMM_BEGIN = 0x1000000L.U(26.W)
  val NVMM_END   = 0x3FFFFFFL.U(26.W)
  val PM_BEGIN   = NVMM_BEGIN
  val PM_END     = 0x1180000L.U(26.W) // 96-MiB
}
```

# I/O
- OpenMPE use the AXI4-Full for I/O protocol.
  - can be ported to bus protocols compatible with AXI4 such as TileLink. 
- Some signals that are ignored are omitted.

## CPU Side
- Optimized for cacheline size transfer
  - 64-bits per beat, 8 beat
- signal width
  - `aXid`, `Xid`: 4-bits
  - `aXaddr`: 26-bits
  - `Xdata`: 64-bits
    - `wstrb` is fixed as 0xFF (All 64-bits in beat are valid)

## Memory Side
- Optimized for cacheline size transfer
  - 512-bits per beat, 1 beat
- signal width
  - `aXid`, `Xid`: 4-bits
  - `aXaddr`: 26-bits
  - `Xdata`: 512-bits
    - `wstrb` is fixed as 0xFFFFFFFFFFFFFFFF (All 512-bits in beat are valid)

## AR channel
- Implemented (must be connected to ports)
  - `arvalid`
  - `arready`
  - `arid`: 4-bits
  - `araddr`: 26-bits (low 6-bits are ommited)
- Fixed (used in modules as FIXED values)
  - `arlen`
  - `arsize` 
- Ignored (NO ports)
  - `arburst`
  - `arlock`
  - `arcache`
  - `arprot`
  - `arqos`

## R channel
- Implemented (must be connected to ports)
  - `rvalid`
  - `rready`
  - `rid`
  - `rdata`
  - `rresp`
  - `rlast`

## AW channel
- Same as AR channel

## W channel
- Implemented (must be connected to ports)
  - `wvalid`
  - `wready`
  - `wdata`: -bits
- Fixed (used in modules as FIXED values)
  - `wstrb`
  - `wlast` 

## B channel
- Implemented (must be connected to ports)
  - `bvalid`
  - `bresp`
  - `bid`
  - `bresp`




# LICENSE
- This repository is open-sourced under Apache 2.0 license.
- Please read the LICENSE file for details.

# Publication / Citation
- When you want to use this MPE, please cite the below paper.
- **TBD**

# Contacts
- If you have any problem, please contact me:
  - oy `_AT_` kasahara.cs.waseda.ac.jp 
