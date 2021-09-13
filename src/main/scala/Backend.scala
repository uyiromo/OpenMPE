// See LICENSE for license details.
package openmpe

import chisel3._
import chisel3.util._
import chisel3.util.Enum
import chisel3.stage.ChiselStage
import chisel3.util.experimental._
import chisel3.experimental.chiselName
import chisel3.util.experimental.BoringUtils

/**
  * Frontend module
  * CPU <--> Frontend <--> Tree <--> Backend <--> MIG
  */


// Backend In/Out
class BackendIOBundle(numTree: Int, idBits: Int, dataBits: Int) extends Bundle {
  val mem  = new AXI4BusS(idBits, dataBits)           // to mem
  val sink = Vec(numTree+1, Flipped(new InternalBus)) // 0: Frontend, 1...t: Tree

  override def cloneType = (new BackendIOBundle(numTree, idBits, dataBits)).asInstanceOf[this.type]
}


class Backend(numTree: Int) extends MultiIOModule {

  // constants
  val idBits       = 4
  val dataBits     = 512
  val numTreeWidth = log2Up(numTree) // width of numTree in binary

  // Top Module I/O
  val io = IO(new BackendIOBundle(numTree, idBits, dataBits))

  // print message for debug
  val debug = !true.B

  // source state machine
  val memIdle :: memAR :: memR :: memR2 :: memAW :: memW :: memW2 :: memB :: Nil = Enum(8)
  val memState = RegInit(memIdle)

  // AR/AW channel
  val axid    = Reg(UInt(idBits.W))
  val axaddr  = Reg(UInt(32.W))
  val arvalid = Reg(Bool())
  io.mem.ar.bits.id    := axid
  io.mem.ar.bits.addr  := axaddr
  io.mem.aw.bits.id    := axid
  io.mem.aw.bits.addr  := axaddr
  io.mem.ar.valid      := (memState === memAR)
  io.mem.aw.valid      := (memState === memAW)

  // W channel
  //val wdata = Reg(Vec(2, UInt(256.W)))
  val wdata = Reg(UInt(512.W))
  io.mem.w.bits.data := wdata
  //io.mem.w.bits.data := Mux((memState === memW), wdata(0), wdata(1))
  //io.mem.w.bits.last := (memState === memW2)
  io.mem.w.valid     := (memState === memW) | (memState === memW2)

  // R/B channel
  val xid       = Reg(UInt(4.W))
  //val rdata     = Reg(Vec(2, UInt(256.W)))
  val rdata     = Reg(UInt(512.W))
  io.mem.r.ready := (memState === memR) | (memState === memR2)
  io.mem.b.ready := (memState === memB)

  // R/W
  val rwRead  = true.B
  val rwWrite = false.B

  // Sink Roundrobin Arbiter
  val numSink      = numTree + 1
  val numSinkWidth = log2Up(numSink)
  val arbiter      = Module(new FCFSArbiter(numSink))
  val sinkValids   = Wire(Vec(numSink, Bool()))
  io.sink.zipWithIndex.foreach { case(s: InternalBus, i: Int) => sinkValids(i) := s.master.valid }
  arbiter.io.clock  := clock.asUInt
  arbiter.io.reset  := reset.asUInt
  arbiter.io.valids := sinkValids.asTypeOf(UInt(numSink.W))

  val sinkIdle :: sinkBusy :: Nil = Enum(2)
  val sinkState  = RegInit(sinkIdle)
  val sinkChoice = Reg(UInt(numSinkWidth.W))
  val resp       = Reg(UInt(2.W))


  /*val ila = Module(new ila_2)
  ila.io.clk    := clock
  ila.io.probe0 := arbiter.io.valids
  ila.io.probe1 := arbiter.io.dequeue
  ila.io.probe2 := io.sink(0).slave.valid
  ila.io.probe3 := io.sink(1).slave.valid
  ila.io.probe4 := io.sink(2).slave.valid
  ila.io.probe5 := io.sink(3).slave.valid
  ila.io.probe6 := io.sink(4).slave.valid*/


  /*
   *
   * Sink state machine
   *
   */
  io.sink.foreach(_.master.nodeq())
  for (i <- 0 to numSink - 1) {
    io.sink(i).slave.valid     := (sinkState) & (memState === memIdle) & (sinkChoice === i.U)
    io.sink(i).slave.bits.id   := xid
    io.sink(i).slave.bits.resp := resp
    io.sink(i).slave.bits.data := rdata.asTypeOf(UInt(512.W))
  }

  arbiter.io.dequeue := false.B
  when (sinkState === sinkIdle) {
    // if sink has request, accept it
    when (!arbiter.io.empty) {
      arbiter.io.dequeue := true.B

      val choice_t = arbiter.io.choice
      val sbits_t  = io.sink(choice_t).master.deq()
      val id_t     = sbits_t.id
      val addr_t   = sbits_t.addr
      val data_t   = sbits_t.data
      val rw_t     = sbits_t.rw

      printf_dbg("  Backend::sink (choice: %d)\n", arbiter.io.choice)
      printf_dbg("      id: %d\n", id_t)
      printf_dbg("    addr: %x\n", addr_t)
      printf_dbg("    data: %x\n", data_t)
      printf_dbg("      rw: %b\n", rw_t)

      sinkChoice := choice_t

      // common
      axid      := id_t
      axaddr    := addr_t
      wdata     := data_t
      //wdata     := data_t.asTypeOf(Vec(2, UInt(256.W)))
      sinkState := sinkBusy

      when (rw_t === rwRead) {
        memState   := memAR
      } .elsewhen (rw_t === rwWrite) {
        memState   := memAW
      }
    }
    // end of ``when (sink_state === sink_idle)''

  }
  when (sinkState === sinkBusy) {
    // wait for axi4 R/B channel, then return it
    val sbus_t = io.sink(sinkChoice).slave
    when (sbus_t.valid & sbus_t.ready) {
      sinkState := sinkIdle

      printf_dbg("  Backend::sink::R/B\n")
      printf_dbg("      id: %d\n", xid)
      printf_dbg("    data: %x\n", rdata.asTypeOf(UInt(512.W)))
    }
  }





  /*
   *
   * AXI4 state machine
   *
   */
  when (memState === memIdle) {
    // do nothing

  }
  when (memState === memAR) {
    // wait for axi4 AR handshake
    when (io.mem.ar.ready) {
      memState := memR
      printf_dbg("  Backend::mem::AR\n")
    }

  }
  when (memState === memAW) {
    // wait for axi4 AW handshake
    when (io.mem.aw.ready) {
      memState := memW
      printf_dbg("  Backend::mem::AW\n")
    }

  }
  when (memState === memW) {
    // write W channel, 1st beat
    when (io.mem.w.ready) {
      memState := memB
      //memState := memW2
      printf_dbg("  Backend::mem::W\n")
      printf_dbg("    data: %x\n", io.mem.w.bits.data)
    }
  }
  /*when (memState === memW2) {
    // write W channel, 2nd beat
    when (io.mem.w.ready) {
      memState := memB
      printf_dbg("  Backend::mem::W2\n")
      printf_dbg("    data: %x\n", io.mem.w.bits.data)
    }
  }*/
  when (memState === memR) {
    // read R channel, 1 burst
    when (io.mem.r.valid) {
      xid      := io.mem.r.bits.id
      //rdata(0) := io.mem.r.bits.data
      rdata := io.mem.r.bits.data
      memState := memIdle
      //memState := memR2
      resp     := "b11".U(2.W)

      printf_dbg("  Backend::mem::R\n")
      printf_dbg("      id: %d\n", io.mem.r.bits.id)
      printf_dbg("    data: %x\n", io.mem.r.bits.data)
    }
  } /*.elsewhen (memState === memR2) {
    // read R channel, 1 burst
    when (io.mem.r.valid) {
      rdata(1) := io.mem.r.bits.data
      memState := memIdle

      printf_dbg("  Backend::mem::R\n")
      printf_dbg("      id: %d\n", io.mem.r.bits.id)
      printf_dbg("    data: %x\n", io.mem.r.bits.data)
    }
  }*/ .elsewhen (memState === memB) {
    // wait B channel
    when (io.mem.b.valid) {
      xid      := io.mem.b.bits.id
      memState := memIdle
      resp     := "b01".U(2.W)

      printf_dbg("  Backend::mem::B\n")
      printf_dbg("      id: %d\n", io.mem.b.bits.id)
    }
  }


  def printf_dbg(fmt: String, data: Bits*): Unit = {
    when (debug) {
      printf.apply(Printable.pack(fmt, data:_*))
    }
  }
}






/** object to generate verilog
  */
object BackendDriver extends App {
  Driver.emitVerilog(new Backend(8))
}
