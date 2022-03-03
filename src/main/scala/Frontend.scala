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

// Frontend In/Out
// ``numTree'' is the number of integrity tree
class FrontendIOBundle(numTree: Int, idBits: Int, dataBits: Int) extends Bundle {
  val cpu     = Flipped(new AXI4Bus(idBits, dataBits)) // slave of CPU
  val tree    = Vec(numTree, new InternalBus)          // master of Tree
  val root    = Vec(numTree, Output(UInt(56.W)))       // root that got from root
  val backend = new InternalBus                        // master of backend

  override def cloneType = (new FrontendIOBundle(numTree, idBits, dataBits)).asInstanceOf[this.type]
}


class Frontend(numTree: Int) extends MultiIOModule {

  // constants
  val idBits       = 4
  val dataBits     = 64
  val numTreeWidth = log2Up(numTree) // width of numTree in binary
  val numRoot      = 384             // protectable memory size: 256-KiB * numRoots (384 -> 96-MiB)
  val numRootWidth = log2Up(numRoot)

  // Top Module I/O
  val io = IO(new FrontendIOBundle(numTree, idBits, dataBits))

  // print message for debug
  val debug = !true.B

  // r/w
  val rwRead  = true.B
  val rwWrite = false.B

  // AXI AR/AW/W/B channel
  //val cpuSinkIdle :: cpuSinkWBusy :: cpuSinkIssue :: Nil = Enum(3)
  //  00             01              10              11
  val cpuSinkIdle :: cpuSinkWBusy :: cpuSinkIssue :: cpuSinkB :: Nil = Enum(4)
  val cpuSinkState = RegInit(cpuSinkIdle)
  val cpuSinkIssueing = Wire(Bool())
  cpuSinkIssueing := (cpuSinkState === cpuSinkIssue)

  // AXI4 R/B channel
  //val cpuSourceBackend :: cpuSourceTree :: cpuSourceR :: cpuSourceB :: Nil = Enum(4)
  val cpuSourceBackend :: cpuSourceTree :: cpuSourceR :: Nil = Enum(3)
  val cpuSourceState = RegInit(cpuSourceBackend)

  // buffer for AR/AW channel
  // other ports can be ignored because CPU always access to memory at the constant granularity
  // 64 bits/beat * 8 beats = 64 bytes
  val axid    = Reg(UInt(idBits.W))
  val axaddr  = Reg(UInt(26.W))
  val axrw    = Reg(Bool())
  io.cpu.aw.ready := (cpuSinkState === cpuSinkIdle) & io.cpu.aw.valid
  io.cpu.ar.ready := !io.cpu.aw.ready & (cpuSinkState === cpuSinkIdle) & io.cpu.ar.valid

  // buffer for W channel
  val wdata64x8 = Reg(Vec(8, UInt(64.W)))
  val wdata512  = WireDefault(wdata64x8.asTypeOf(UInt(512.W)))
  val wdataIdx  = Reg(UInt(3.W))
  val wlast     = Wire(Bool())
  io.cpu.w.ready := (cpuSinkState === cpuSinkWBusy)
  wlast          := (wdataIdx === 7.U(3.W))

  // buffer for R/B channel
  val xid      = Reg(UInt(idBits.W))
  val xresp    = Reg(UInt(2.W))

  // buffer for R channel
  val rdata512  = Reg(UInt(512.W))
  val rdata64x8 = WireDefault(rdata512.asTypeOf(Vec(8, UInt(64.W))))
  val rdataIdx  = Reg(UInt(3.W))
  io.cpu.r.bits.id   := xid
  io.cpu.r.bits.data := rdata64x8(rdataIdx)
  io.cpu.r.bits.resp := xresp
  io.cpu.r.bits.last := (rdataIdx === 7.U(3.W))
  io.cpu.r.valid     := (cpuSourceState === cpuSourceR)

  // buffer for B channel
  //io.cpu.b.bits.id   := xid
  //io.cpu.b.bits.resp := xresp
  //io.cpu.b.valid     := (cpuSourceState === cpuSourceB)
  io.cpu.b.bits.id   := axid
  io.cpu.b.bits.resp := "b00".U(2.W)
  io.cpu.b.valid     := (cpuSinkState === cpuSinkB)

  // Mux AR/AW channel
  val axid_t   = Mux(io.cpu.aw.valid, io.cpu.aw.bits.id, io.cpu.ar.bits.id)
  val axaddr_t = Mux(io.cpu.aw.valid, io.cpu.aw.bits.addr, io.cpu.ar.bits.addr)

  // manage root of Integrity Tree
  // rootL3 are must be initialized by n_init
  val n_init           = "x1".U(56.W)                   // multiplicative unit
  /*val rootL3          = Reg(Vec(numRoot, UInt(56.W)))*/
  val rootL3           = SyncReadMem(numRoot, UInt(56.W))
  val nextIssueTree    = RegInit(0.U(numTreeWidth.W))   // index of Tree used by next issue
  val nextReceiveTree  = RegInit(0.U(numTreeWidth.W))   // index of Tree used by next receive

  // RootIdx assigned to tree
  val invalidRootIdx = ((1 << numRootWidth) - 1).U(numRootWidth.W)  // indicate
  val treeRootIdx    = RegInit(VecInit(Seq.fill(numTree)(invalidRootIdx)))

  // Current selected root
  // PM Address: 0x1000000L.U(26.W) - 0x1180000L.U(26.W), so only check (25, 16)
  //val rootL3Idx   = axaddr(20, 12) & 0x1FF.U(9.W)
  val rootL3Idx   = Reg(UInt(numRootWidth.W))
  val rootIdle    = Wire(Bool())
  val rootInitIdx = RegInit(numRoot.U)
  val treeIdle    = Wire(Bool())
  val disableTree = RegInit(false.B)
  val toTree      = !disableTree & (0x100.U <= axaddr(25,16)) & (axaddr(25,16) < 0x118.U)
  val treeIssuable = Wire(Bool())
  rootIdle := (rootInitIdx === numRoot.U) & treeRootIdx.foldLeft(true.B)((acc, idx) => acc & (idx =/= rootL3Idx))
  treeIdle := (treeRootIdx(nextIssueTree) === invalidRootIdx)
  treeIssuable := rootIdle & treeIdle & toTree

  // rootL3 read
  val rootL3Read  = (cpuSinkState === cpuSinkIdle) & (io.cpu.ar.valid | io.cpu.aw.valid)
  val rootL3Idx_t = axaddr_t(20, 12) & 0x1FF.U(9.W)
  val currentRoot = rootL3.read(rootL3Idx_t, rootL3Read)

  // rootL3 writes
  val rootL3WriteInit = (rootInitIdx =/= numRoot.U)
  val nextIssueTreeM  = io.tree(nextIssueTree).master
  val rootL3WriteInc  = nextIssueTreeM.valid & nextIssueTreeM.ready & (nextIssueTreeM.bits.rw === rwWrite)
  val rootL3Write     = rootL3WriteInit | rootL3WriteInc
  val rootL3WriteIdx  = Mux(rootL3WriteInit, rootInitIdx, rootL3Idx)
  val rootL3WriteData = Mux(rootL3WriteInit, n_init, increment(currentRoot))

  // Trigger rootL3 Initialization by writing some into 0xC7FFF000
  when (io.cpu.aw.valid & (io.cpu.aw.bits.addr === "x11FFFC0".U(26.W))) {
    rootInitIdx := 0.U(numRootWidth.W)
  }
  when (rootL3Write) {
    rootL3.write(rootL3WriteIdx, rootL3WriteData)
    rootInitIdx := rootInitIdx + rootL3WriteInit
  }


  // static assign
  io.tree.foreach(_.master.bits.id   := axid)
  io.tree.foreach(_.master.bits.addr := axaddr)
  io.tree.foreach(_.master.bits.data := wdata512)
  io.tree.foreach(_.master.bits.rw   := axrw)
  io.backend.master.bits.id   := axid
  io.backend.master.bits.addr := axaddr
  io.backend.master.bits.data := wdata512
  io.backend.master.bits.rw   := axrw
  //io.root.foreach(_:= rootL3(rootL3Idx))
  io.root.foreach(_:= currentRoot)



  // Disable Integrity Tree when CPU write some into 0xC7FFF040
  //when (axaddr === "x11FFFC1".U(26.W)) {
  //  disableTree := true.B
  //}

  // Disable Integrity Tree when CPU write some into 0xC7FFF080
  //when (axaddr === "x11FFFC2".U(26.W)) {
  //  disableTree := false.B
  //}

  /*val ila = Module(new ila_1)
  ila.io.clk    := clock
  ila.io.probe0 := io.cpu.ar.valid
  ila.io.probe1 := io.cpu.ar.bits.addr
  ila.io.probe2 := io.cpu.ar.bits.id
  ila.io.probe3 := io.cpu.r.valid
  ila.io.probe4 := io.cpu.r.bits.resp
  ila.io.probe5 := io.cpu.r.bits.id*/


  /**
    *
    * CPU Sink state machine (AXI4 AR, AW&W)
    *
    */
  val issueing = cpuSinkState === cpuSinkIssue
  for (i <- 0 to numTree - 1) {
    io.tree(i).master.valid := (cpuSinkIssueing & treeIssuable & (nextIssueTree === i.U))
  }
  io.backend.master.valid := (cpuSinkIssueing & !toTree)
  when (cpuSinkState === cpuSinkIdle) {
    // Write has higher priority than Read

    // common register
    axid      := axid_t
    axaddr    := axaddr_t
    axrw      := Mux(io.cpu.aw.valid, rwWrite, rwRead)
    wdataIdx  := 0.U(3.W)
    rootL3Idx := rootL3Idx_t

    when (io.cpu.aw.valid) {
      // accept AW channel, next todo: W channel
      cpuSinkState    := cpuSinkWBusy

      printf_dbg("  Frontend::cpuSink::AW\n")
      printf_dbg("      id: %x\n", axid_t)
      printf_dbg("    addr: %x\n", axaddr_t)

    } .elsewhen (io.cpu.ar.valid) {
      // read AR channel, next todo: issue to Tree/Backekd
      cpuSinkState    := cpuSinkIssue

      printf_dbg("  Frontend::cpuSink::AR\n")
      printf_dbg("      id: %x\n", axid_t)
      printf_dbg("    addr: %x\n", axaddr_t)
    }

  }
  when (cpuSinkState === cpuSinkWBusy) {
    // under W beats
    when (io.cpu.w.valid) {
      // accept wdata
      val wdata_t          = io.cpu.w.bits.data
      wdata64x8(wdataIdx) := wdata_t
      wdataIdx            := wdataIdx + 1.U(3.W)

      // when wlast is high, go to issue state
      cpuSinkState := Mux(wlast, cpuSinkIssue, cpuSinkWBusy)

      printf_dbg("  Frontend::cpuSink::W / wdata64x8[%d]: %x\n", wdataIdx, wdata_t)
    }

  }
  when (cpuSinkState === cpuSinkIssue) {
    // Try to issue the request to Backend or Tree
    // when target is busy, wait it

    when (toTree & io.tree(nextIssueTree).master.ready) {
      // Check tree index range
      when (debug & (rootL3Idx >= numRoot.U)) {
        printf("rootL3Idx_t(%d) exceeds numRoot(%d)!\n", rootL3Idx, numRoot.U)
        assert(rootL3Idx < numTree.U)
      }

      cpuSinkState  := Mux(axrw === rwWrite, cpuSinkB, cpuSinkIdle)
      nextIssueTree := nextIssueTree + 1.U(numTreeWidth.W)

      // Lock root / root initialization
      treeRootIdx(nextIssueTree) := rootL3Idx

      printf_dbg("  Frontend::cpuSinkIssue::Tree(%d)\n", nextIssueTree)
      printf_dbg("           id: %x\n", axid)
      printf_dbg("         addr: %x\n", axaddr)
      printf_dbg("         data: %x\n", wdata512)
      printf_dbg("           rw: %x\n", axrw)
      printf_dbg("    rootL3Idx: %d\n", rootL3Idx)
      // end of ``when (toTree(axaddr))''

    }
    when (io.backend.master.ready) {
      cpuSinkState  := Mux(axrw === rwWrite, cpuSinkB, cpuSinkIdle)

      printf_dbg("  Frontend::cpuSinkIssue::Backend\n")
      printf_dbg("      id: %x\n", axid)
      printf_dbg("    addr: %x\n", axaddr)
      printf_dbg("    data: %x\n", wdata512)
      printf_dbg("      rw: %x\n", axrw)
    }
    // end of ``elsewhen (cpuSinkState === cpuSinkIssue)''
  }
  when (cpuSinkState === cpuSinkB) {
    // B channel: keep bvalid until handshake established
    val est_t  = io.cpu.b.ready  // established handshake or not
    cpuSinkState  := Mux(est_t, cpuSinkIdle, cpuSinkB)

    printf_dbg("  Frontend::cpuSink::B\n")
    printf_dbg("      id: %x\n", io.cpu.b.bits.id)
    printf_dbg("    resp: %b\n", io.cpu.b.bits.resp)

  }



  /**
   *
   * CPU Source state machine (AXI4 R)
   *
   */
  io.tree.foreach(_.slave.nodeq())
  io.backend.slave.nodeq()
  when (cpuSourceState === cpuSourceBackend) {
    // if Backend have data on slave bus, accept & return it to CPU
    when (io.backend.slave.valid) {
      val sbits_t = io.backend.slave.deq()
      val sid_t   = sbits_t.id
      val sdata_t = sbits_t.data
      val srw_t   = sbits_t.resp(1)

      // common
      xid      := sid_t
      xresp    := "b00".U(2.W)
      rdata512 := sdata_t
      rdataIdx := 0.U(3.W)

      when (srw_t === rwRead) {
        cpuSourceState := cpuSourceR
        printf_dbg("  Frontend::cpuSource::Backend::Read\n")
      }
      when (srw_t === rwWrite) {
        //cpuSourceState := cpuSourceB
        cpuSourceState := cpuSourceTree
        printf_dbg("  Frontend::cpuSource::Backend::Write\n")
      }

    } .otherwise {
      cpuSourceState := cpuSourceTree
    }

  } .elsewhen (cpuSourceState === cpuSourceTree) {

    // if Tree have data on slave bus, accept & return it to CPU
    when (io.tree(nextReceiveTree).slave.valid) {
      val sbits_t = io.tree(nextReceiveTree).slave.deq()
      val sid_t   = sbits_t.id
      val sdata_t = sbits_t.data
      val serr_t  = !sbits_t.resp(0)
      val srw_t   = sbits_t.resp(1)

      // Release lock
      treeRootIdx(nextReceiveTree) := invalidRootIdx
      nextReceiveTree              := nextReceiveTree + 1.U(numTreeWidth.W)

      // common
      xid      := sid_t
      xresp    := Mux(serr_t, "b10".U(2.W), "b00".U(2.W))
      rdata512 := Mux(serr_t, VecInit(Seq.fill(512)(true.B)).asTypeOf(UInt(512.W)), sdata_t)
      rdataIdx := 0.U(3.W)

      when (srw_t === rwRead) {
        cpuSourceState := cpuSourceR
        printf_dbg("  Frontend::cpuSource::Tree(%d)::Read\n", nextReceiveTree)
      }
      when (srw_t === rwWrite) {
        //cpuSourceState := cpuSourceB
        cpuSourceState := cpuSourceBackend
        printf_dbg("  Frontend::cpuSource::Tree(%d)::Write\n", nextReceiveTree)
      }

    } .otherwise {
      cpuSourceState := cpuSourceBackend
    }

  } .elsewhen (cpuSourceState === cpuSourceR) {
    // R channel: keep rvalid until all data have been issued to CPU
    val est_t  = io.cpu.r.ready  // established handshake or not
    val last_t = est_t & io.cpu.r.bits.last       // current beat is last beats or not
    rdataIdx        := Mux(est_t, rdataIdx + 1.U(3.W), rdataIdx)
    cpuSourceState  := Mux(last_t, cpuSourceBackend, cpuSourceR)

    printf_dbg("  Frontend::cpuSource::R / rdata[%d]: %x\n", rdataIdx, io.cpu.r.bits.data)
    when (debug & last_t) {
      printf_dbg("      id: %x\n", io.cpu.r.bits.id)
      printf_dbg("    resp: %b\n", io.cpu.r.bits.resp)
    }

  }
    /*.elsewhen (cpuSourceState === cpuSourceB) {
    // B channel: keep bvalid until handshake established
    val est_t  = io.cpu.b.ready  // established handshake or not
    cpuSourceState  := Mux(est_t, cpuSourceBackend, cpuSourceB)

    printf_dbg("  Frontend::cpuSource::B\n")
    printf_dbg("      id: %x\n", io.cpu.b.bits.id)
    printf_dbg("    resp: %b\n", io.cpu.b.bits.resp)

  }*/


  /** Increment counter on GF(2^56) */
  def increment(cntr: UInt): UInt = {
    // m55 = -cntr(55)
    // if cntr(55) = 1, -1 (0xFFFFFF...)
    // if cntr(55) = 0, 0
    val m55 = Mux(cntr(55), "xFFFFFFFFFFFFFF".U(56.W), 0.U(56.W))
    ((cntr << 1) ^ ("x80000C00000001".U(56.W) & m55))(55, 0)
  }

  def printf_dbg(fmt: String, data: Bits*): Unit = {
    when (debug) {
      printf.apply(Printable.pack(fmt, data:_*))
    }
  }
}



/** object to generate verilog
  */
object FrontendDriver extends App {
  Driver.emitVerilog(new Frontend(8))
}
