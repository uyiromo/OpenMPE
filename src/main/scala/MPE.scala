// See LICENSE for license details.
package openmpe

import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage
import chisel3.util.experimental._
import chisel3.experimental.chiselName
import chisel3.util.experimental.BoringUtils

/**
  * A wrapper module for Memory Protection Engine
  */
// MPE In/Out
@chiselName
class MPEIOBundle extends Bundle {
  val cpu = Flipped(new AXI4Bus(4, 64))  // from CPU
  val mem = new AXI4BusS(4, 512)          // to mem
}

// ``t'' is the number of integrity tree
@chiselName
class MPE(numTree: Int) extends Module {

  // constraints
  require(numTree % 2 == 0, s"numTree must be power of two (required: ${numTree})")

  // Top Module IO
  val io = IO(new MPEIOBundle())

  // modules
  val frontend = Module(new Frontend(numTree))
  val backend  = Module(new Backend(numTree))
  val tree     = VecInit(Seq.fill(numTree) { Module(new Tree).io })

  // connections
  backend.io.sink(0) <> frontend.io.backend
  for (i <- 0 to numTree - 1) {
    tree(i).fe           <> frontend.io.tree(i)
    tree(i).root         <> frontend.io.root(i)
    backend.io.sink(i+1) <> tree(i).be
  }
  frontend.io.cpu <> io.cpu
  io.mem          <> backend.io.mem
}

/** object to generate verilog
  */
object MPEDriver extends App {
  //(new chisel3.stage.ChiselStage).emitFirrtl(new MPE(8))
  //(new chisel3.stage.ChiselStage).emitVerilog(new MPE(8))
  Driver.emitVerilog(new MPE(8))
}
