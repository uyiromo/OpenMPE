// See LICENCE for license details.
package openmpe

import chisel3._
import chisel3.util._
import chisel3.util.Enum
import chisel3.stage.ChiselStage
import chisel3.util.experimental._
import chisel3.experimental.chiselName
import chisel3.util.experimental.BoringUtils

// Master bus between modules
class InternalBusM extends Bundle {
  val id   = UInt(4.W)
  val addr = UInt(26.W)
  val data = UInt(512.W)
  val rw   = Bool()
}

// Slave bus between modules
class InternalBusS extends Bundle {
  val id   = UInt(4.W)
  val resp = UInt(2.W)
  val data = UInt(512.W)
}

class InternalBus extends Bundle {
  val master = Decoupled(new InternalBusM)
  val slave  = Flipped(Decoupled(new InternalBusS))

  override def cloneType = (new InternalBus).asInstanceOf[this.type]
}
