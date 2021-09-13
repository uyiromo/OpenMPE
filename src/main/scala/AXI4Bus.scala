// See LICENSE for license details.
package nvsit

import chisel3._
import chisel3.util._
import chisel3.util.Enum
import chisel3.stage.ChiselStage
import chisel3.util.experimental._
import chisel3.experimental.chiselName
import chisel3.util.experimental.BoringUtils

// AXI4-full channels as master


// Simplefied AXI4 AR/AW
class AXI4BusAS(idBits: Int) extends Bundle {
  val id    = UInt(idBits.W)
  val addr  = UInt(26.W)
  //val burst = UInt(2.W) // ignored
  //val lock  = UInt(1.W) // ignored
  //val cache = UInt(4.W) // ignored
  //val prot  = UInt(3.W) // ignored
  //val qos   = UInt(4.W) // ignored

  override def cloneType = (new AXI4BusAS(idBits)).asInstanceOf[this.type]
}

// AXI4 AR/AW
class AXI4BusA(idBits: Int) extends AXI4BusAS(idBits) {
  //val len   = UInt(8.W)
  //val size  = UInt(3.W)

  override def cloneType = (new AXI4BusA(idBits)).asInstanceOf[this.type]
}



// Simplified AXI4 R
class AXI4BusRS(idBits: Int, dataBits: Int) extends Bundle {
  val id    = UInt(idBits.W)
  val data  = UInt(dataBits.W)
  //val resp  = UInt(2.W)
  //val last  = Bool()

  override def cloneType = (new AXI4BusRS(idBits, dataBits)).asInstanceOf[this.type]
}

// AXI4 R
class AXI4BusR(idBits: Int, dataBits: Int) extends AXI4BusRS(idBits, dataBits) {
  val resp  = UInt(2.W)
  val last  = Bool()

  override def cloneType = (new AXI4BusR(idBits, dataBits)).asInstanceOf[this.type]
}




// Simplified AXI4 W
class AXI4BusWS(dataBits: Int) extends Bundle {
  val data  = UInt(dataBits.W)
  //val strb  = UInt((dataBits/8).W)
  //val last  = Bool()

  override def cloneType = (new AXI4BusWS(dataBits)).asInstanceOf[this.type]
}

// AXI4 W
class AXI4BusW(dataBits: Int) extends AXI4BusWS(dataBits) {
  //val strb  = UInt((dataBits/8).W)
  //val last  = Bool()

  override def cloneType = (new AXI4BusW(dataBits)).asInstanceOf[this.type]
}



// Simplified AXI4 B
class AXI4BusBS(idBits: Int) extends Bundle {
  val id    = UInt(idBits.W)
  //val resp  = UInt(2.W)

  override def cloneType = (new AXI4BusB(idBits)).asInstanceOf[this.type]
}

// AXI4 B
class AXI4BusB(idBits: Int) extends AXI4BusBS(idBits) {
  val resp  = UInt(2.W)

  override def cloneType = (new AXI4BusB(idBits)).asInstanceOf[this.type]
}


class AXI4Bus(idBits: Int, dataBits: Int) extends Bundle {
  // AR, AW, R, W, B
  val ar = Decoupled(new AXI4BusA(idBits))
  val aw = Decoupled(new AXI4BusA(idBits))
  val r  = Flipped(Decoupled(new AXI4BusR(idBits, dataBits)))
  val w  = Decoupled(new AXI4BusW(dataBits))
  val b  = Flipped(Decoupled(new AXI4BusB(idBits)))

  override def cloneType = (new AXI4Bus(idBits, dataBits)).asInstanceOf[this.type]
}

// Simplified
class AXI4BusS(idBits: Int, dataBits: Int) extends Bundle {
  // AR, AW, R, W, B
  val ar = Decoupled(new AXI4BusAS(idBits))
  val aw = Decoupled(new AXI4BusAS(idBits))
  val r  = Flipped(Decoupled(new AXI4BusRS(idBits, dataBits)))
  val w  = Decoupled(new AXI4BusWS(dataBits))
  val b  = Flipped(Decoupled(new AXI4BusBS(idBits)))

  override def cloneType = (new AXI4BusS(idBits, dataBits)).asInstanceOf[this.type]
}
