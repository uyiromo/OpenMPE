// See LICENSE for license details.
package openmpe

import chisel3._
import chisel3.util._
import chisel3.util.Enum
import chisel3.stage.ChiselStage
import chisel3.util.experimental._
import chisel3.experimental.chiselName
import chisel3.util.experimental.BoringUtils
import chisel3.core.dontTouch

// cache lines
class CacheLine extends Bundle {
  val unused = UInt(8.W)
  val tag    = UInt(56.W)
  val nonce  = Vec(8, UInt(56.W))
}


// Tree Top In/Out
class TreeIOBundle extends Bundle {
  val fe       = Flipped(new InternalBus)  // Frontend
  val be       = new InternalBus           // Backend
  val root     = Input(UInt(56.W))         // root nonce
}

/**
  * Integrity Tree
  */
class Tree extends MultiIOModule {

  // Top Module I/O
  val io = IO(new TreeIOBundle)

  // verbose output
  val debug = !true.B

  // Constants
  val n_init  = WireDefault("x1".U(56.W))                       // multiplicative unit
  val base_pm = WireDefault(AddressMap.PM_BEGIN)
  val base_pd = WireDefault(AddressMap.PM_BEGIN + 0x0180000L.U) // base + 0x06000000.U
  val base_cr = WireDefault(AddressMap.PM_BEGIN + 0x01B0000L.U) // base + 0x06C00000.U
  val base_l0 = WireDefault(AddressMap.PM_BEGIN + 0x01F8000L.U) // base + 0x07E00000.U
  val base_l1 = WireDefault(AddressMap.PM_BEGIN + 0x01FF000L.U) // base + 0x07FC0000.U
  val base_l2 = WireDefault(AddressMap.PM_BEGIN + 0x01FFE00L.U) // base + 0x07FF8000.U
  val base_l3 = WireDefault(AddressMap.PM_BEGIN + 0x01FFFC0L.U) // base + 0x07FFF000.U  // on-die

  val PM_SIZE       = (96*1024*1024) >> 6  // 96MiB shift left by 6 bits (hex: 0x180000)
  val PM_SIZE_WIDTH = log2Up(PM_SIZE)      // 21 for 96 MiB

  // modules
  val cwmac = Module(new CWMACOpt)
  val aes128 = Module(new AES128Opt(4))

  // levels
  val lv_l2   = 0.U(3.W)
  val lv_l1   = 1.U(3.W)
  val lv_l0   = 2.U(3.W)
  val lv_cr   = 3.U(3.W)
  val lv_tag  = 4.U(3.W)
  val lv_mem  = 5.U(3.W)

  // internal
  val id         = Reg(UInt(4.W))
  val data       = Reg(UInt(512.W))
  val root       = Reg(UInt(56.W))
  val pm_offset  = Reg(UInt(PM_SIZE_WIDTH.W)) // offset in protected memory

  // cachelines
  val cacheline       = Reg(Vec(6, new CacheLine))
  val cachelineMem    = WireDefault(cacheline(lv_mem).asTypeOf(UInt(512.W)))
  val cachelineAddr   = Wire(Vec(6, UInt(26.W)))   // addresses of L2, L1, L0, CR, Tag, Mem
  val cachelineValid  = Reg(Vec(6, Bool()))  // cacheline is valid (loaded/stored) or not
  val cachelineStored = Wire(Bool())                // all cachelines have been loaded/stored
  val nonceIdx        = Wire(Vec(5, UInt(3.W)))    // nonce index of L2, L1, L0, CR, Tag

  // tags
  //val cwmacTagsCompleted = Reg(Bool()) // all tags have been computed already
  val cwmacTagsCompleted = Wire(Bool()) // all tags have been computed already
  val cwmacTagsEqual = Reg(Bool()) // all tags are expected or not
  val verified       = Wire(Bool())
  val uninit         = Wire(Bool())
  val resp           = Reg(UInt(2.W))
  uninit   := (cacheline(lv_cr).nonce(nonceIdx(lv_cr)) === n_init)
  verified := (root =/= 0.U(56.W)) & (cwmacTagsEqual | uninit)

  // Tree states
  //  000         001           010         011           100
  val treeIdle :: treeVerify :: treeComp :: treeUpdate :: treeRelease :: Nil = Enum(5)
  val treeState = RegInit(treeIdle)

  // Crypt States
  val aesIdle :: aesWait :: aesBusy :: Nil = Enum(3)
  val aesState    = RegInit(aesIdle)
  val cipherValid = Wire(Bool())
  val cipher      = Wire(UInt(512.W))
  cipherValid := aes128.io.cipherValid
  cipher      := aes128.io.cipher

  // CWMAC States
  //  000          001                 010               011                 100
  val cwmacIdle :: cwmacVerifyIssue :: cwmacVerifyBusy ::cwmacUpdateIssue :: cwmacUpdateBusy:: Nil = Enum(5)
  val cwmacState    = RegInit(cwmacIdle)
  val cwmacLevel    = Reg(UInt(3.W))

  // memory access request
  val rwRead  = true.B
  val rwWrite = false.B
  val rwState = Reg(Bool())

  //  00         01          10
  val memIdle :: memIssue :: memBusy :: Nil = Enum(3)
  val memState    = RegInit(memIdle)
  val memOrder    = Reg(Vec(5, UInt(3.W)))
  val memOrderIdx = Reg(UInt(3.W))
  val memLevel    = Reg(UInt(3.W))
  val memRW       = Reg(Bool())
  io.be.master.bits.rw := memRW


  // static assigns
  // key for AES-128 and Hash function
  val key128 = "x000102030405060708090a0b0c0d0e0f".U(128.W)
  val key512 = "xfedcba9876543210fedcba9876543210ffeeddccbbaa998877665544332211000123456789abcdef0123456789abcdef00112233445566778899aabbccddeeff".U(512.W)
  cwmac.io.keyHash := key512
  cwmac.io.keyEnc  := key128
  aes128.io.key    := key128

  // cacheline address in SIT region, and index of nonces
  cachelineAddr(lv_l2) := base_l2 + (pm_offset(PM_SIZE_WIDTH - 1, 12))
  nonceIdx(lv_l2)      := pm_offset(11, 9)

  cachelineAddr(lv_l1) := base_l1 + (pm_offset(PM_SIZE_WIDTH - 1, 9))
  nonceIdx(lv_l1)      := pm_offset(8, 6)

  cachelineAddr(lv_l0) := base_l0 + (pm_offset(PM_SIZE_WIDTH - 1, 6))
  nonceIdx(lv_l0)      := pm_offset(5, 3)

  cachelineAddr(lv_cr) := base_cr + (pm_offset(PM_SIZE_WIDTH - 1, 3))
  nonceIdx(lv_cr)      := pm_offset(2, 0)

  cachelineAddr(lv_tag) := base_pd + (pm_offset(PM_SIZE_WIDTH - 1, 3))
  nonceIdx(lv_tag)      := pm_offset(2, 0)

  cachelineAddr(lv_mem) := base_pm + pm_offset

  /*val ila = ILA(clock, Seq(
    io.fe.master.bits.addr,
    memLevel,
    cwmacLevel,
    treeState,
    io.be.master.bits.addr,
    io.be.slave.bits.data,
    io.root,
    cwmacTagsEqual,
    verified,
    io.fe.slave.bits.resp
  ))*/

  /**
   *
   *  memory access state machine
   *
    */
  io.be.master.valid := (memState === memIssue) & (((!memRW) & cachelineValid(memLevel)) | (memRW))
  io.be.master.bits.id   := id
  io.be.master.bits.addr := cachelineAddr(memLevel)
  io.be.master.bits.data := cacheline(memLevel).asTypeOf(UInt(512.W))
  io.be.slave.nodeq()
  cachelineStored := (memOrderIdx === 6.U(3.W))
  when (memState === memIdle) {

  }
  when (memState === memIssue) {
    // Try to issue new request to backend

    // Try to issue new request
    val addr_t = cachelineAddr(memLevel)
    val data_t = cacheline(memLevel).asTypeOf(UInt(512.W))
    val id_t   = id
    //io.be.master.bits.addr := addr_t
    //io.be.master.bits.data := data_t
    //io.be.master.bits.id   := id_t

    memState := Mux(io.be.master.ready, memBusy, memIssue)

    printf_dbg("  Tree::Mem::Issue (rw: %b, level: %d)\n", memRW, memLevel)
    printf_dbg("      addr: %x\n", addr_t)
    printf_dbg("      data: %x\n", data_t)
    printf_dbg("        id: %x\n", id_t)

  }
  when (memState === memBusy) {
    // Waiting backend response

    when (io.be.slave.valid) {
      // Deq
      val bits_t = io.be.slave.deq()
      val id_t   = bits_t.id
      val data_t = bits_t.data
      cachelineValid(memLevel) := true.B

      // Parse
      cacheline(memLevel) := data_t.asTypeOf(new CacheLine)

      // state transition
      memLevel    := memOrder(memOrderIdx)
      memOrderIdx := memOrderIdx + 1.U(3.W)
      memState    := Mux(memOrderIdx === 5.U(3.W), memIdle, memIssue)

      printf_dbg("  Tree::Mem::Busy (rw: %b, level: %d)\n", memRW, memLevel);
      printf_dbg("      id: %x\n", id_t)
      printf_dbg("    data: %x\n", data_t)

    } // end of ``when (io.be.slave.valid)''
    // end of ``when (memState === memBusy)''
  } // end of Memory State Machine



  /**
   *
   *  CWMAC state machine
   *
   */
  cwmac.io.source.noenq()
  cwmac.io.tag.nodeq()
  cwmacTagsCompleted := cwmacLevel === lv_mem
  when (cwmacState === cwmacIdle) {
    // wait for treeVerify or treeUpdate
  }
  when (cwmacState === cwmacVerifyIssue) {
    // Try to issue new request to the CWMAC module

    val cwmacLevel_t = Mux(cwmacLevel === lv_tag, lv_mem, cwmacLevel)
    when (cachelineValid(cwmacLevel_t)) {
      // can issue only when the corresponding cacheline has been loaded

      // Enq
      val addr_t  = cachelineAddr(cwmacLevel_t)
      val nonce_t = MuxLookup(cwmacLevel_t, root,  // lv_l2 is mapped to default case
          Seq(
            lv_l1  -> cacheline(lv_l2).nonce(nonceIdx(lv_l2)),
            lv_l0  -> cacheline(lv_l1).nonce(nonceIdx(lv_l1)),
            lv_cr  -> cacheline(lv_l0).nonce(nonceIdx(lv_l0)),
            lv_mem -> cacheline(lv_cr).nonce(nonceIdx(lv_cr))
          ))
      val msg_t = cacheline(cwmacLevel_t).nonce.asTypeOf(UInt(448.W))
      cwmac.io.source.bits.addr  := addr_t
      cwmac.io.source.bits.nonce := nonce_t
      cwmac.io.source.bits.msg   := msg_t
      cwmac.io.source.valid      := true.B

      // state transition
      cwmacState := cwmacVerifyBusy

      printf_dbg("  Tree::CWMAC::Issue (level: %d)\n", cwmacLevel_t)
      printf_dbg("    addr  = %x\n", addr_t)
      printf_dbg("    nonce = %x\n", nonce_t)
      printf_dbg("    msg   = %x\n", msg_t)

    } // end of ''when (cachelineValid(cwmacLevel))''
  }
  when (cwmacState === cwmacVerifyBusy) {
    // waiting for the cwmac module

    when (cachelineValid(cwmacLevel) & cwmac.io.tag.valid) {
      // Deq & Replace
      val tag_t = cwmac.io.tag.deq()

      // Compare Tag
      val expected_t = Mux(cwmacLevel === lv_tag,
        cacheline(lv_tag).nonce(nonceIdx(lv_tag)),
        cacheline(cwmacLevel).tag)
      cwmacTagsEqual := cwmacTagsEqual & (tag_t === expected_t)
      printf_dbg("  Tree::CWMAC::Compare (level: %d)\n", cwmacLevel)
      printf_dbg("    Expected: %x, Computed: %x\n", expected_t, tag_t)

      // state transition
      cwmacLevel := cwmacLevel + 1.U(3.W)
      cwmacState := Mux(cwmacLevel === lv_tag, cwmacIdle, cwmacVerifyIssue)
    }
  }
  when (cwmacState === cwmacUpdateIssue) {
    // Try to issue new request to the CWMAC module

    val cwmacLevel_t = Mux(cwmacLevel === lv_tag, lv_mem, cwmacLevel)

    // Enq
    val addr_t  = cachelineAddr(cwmacLevel_t)
    val nonce_t = MuxLookup(cwmacLevel_t, root,  // lv_l2 is mapped to default case
      Seq(
        lv_l1  -> cacheline(lv_l2).nonce(nonceIdx(lv_l2)),
        lv_l0  -> cacheline(lv_l1).nonce(nonceIdx(lv_l1)),
        lv_cr  -> cacheline(lv_l0).nonce(nonceIdx(lv_l0)),
        lv_mem -> cacheline(lv_cr).nonce(nonceIdx(lv_cr))
      ))
    val msg_t = cacheline(cwmacLevel_t).nonce.asTypeOf(UInt(448.W))
    cwmac.io.source.bits.addr  := addr_t
    cwmac.io.source.bits.nonce := nonce_t
    cwmac.io.source.bits.msg   := msg_t
    cwmac.io.source.valid      := true.B

    // state transition
    cwmacState := cwmacUpdateBusy

    printf_dbg("  Tree::CWMAC::Issue (level: %d)\n", cwmacLevel_t)
    printf_dbg("    addr  = %x\n", addr_t)
    printf_dbg("    nonce = %x\n", nonce_t)
    printf_dbg("    msg   = %x\n", msg_t)

  } // end of ''when (cachelineValid(cwmacLevel))''
  when (cwmacState === cwmacUpdateBusy) {
    // waiting for the cwmac module

    when (cwmac.io.tag.valid) {
      // Deq & Replace
      val tag_t = cwmac.io.tag.deq()

      // Replace Tag
      when (cwmacLevel === lv_tag) {
        cacheline(lv_tag).nonce(nonceIdx(lv_tag)) := tag_t
      } .otherwise {
        cacheline(cwmacLevel).tag  := tag_t
      }

      // cacheline.tag is valid now
      cachelineValid(cwmacLevel) := true.B

      // state transition
      cwmacLevel := cwmacLevel - 1.U(3.W)
      cwmacState := Mux(cwmacLevel === lv_l2, cwmacIdle, cwmacUpdateIssue)
    }
  }



  /**
   *
   *  AES state machine
   *
   */
  aes128.io.plain.noenq()
  when (aesState === aesIdle) {
    // waiting for tree state indicator
  }
  when (aesState === aesWait) {
    // waiting for CoveredRegion, then Put it on AES modules
    // when read, prepare for decipher, when write, prepare for cipher

    val nonce_t0 = cacheline(lv_cr).nonce(nonceIdx(lv_cr))
    val nonce_t  = Mux(rwState === rwRead, nonce_t0, increment(nonce_t0))
    aes128.io.plain.bits.addr  := cachelineAddr(lv_mem)
    aes128.io.plain.bits.nonce := nonce_t

    when (cachelineValid(lv_cr)) {
      aes128.io.plain.valid := true.B
      aesState := aesIdle

      printf_dbg("  Tree::AESx4 issue\n")
      printf_dbg("     addr: %x\n", cachelineAddr(lv_mem))
      printf_dbg("    nonce: %x\n", nonce_t)
    }
  }


  /**
    *
    * Tree State Machine
    *
    */
  io.fe.slave.valid := (treeState === treeRelease) & ((rwState) | (!rwState & cachelineStored))
  io.fe.slave.bits.id   := id
  io.fe.slave.bits.resp := resp
  io.fe.slave.bits.data := (cachelineMem ^ cipher)
  io.fe.master.nodeq()
  when (treeState === treeIdle) {
    // waiting for Frontend.valid

    when (io.fe.master.valid) {
      val bits_t  = io.fe.master.deq()
      val mid_t   = bits_t.id
      val maddr_t = bits_t.addr(PM_SIZE_WIDTH, 0)
      val mdata_t = bits_t.data
      val mrw_t   = bits_t.rw
      id        := mid_t
      data      := mdata_t
      pm_offset := maddr_t
      rwState   := mrw_t

      // try to verify tree
      root       := io.root
      treeState  := treeVerify

      /*
       * Invoke AES state machine
       */
      aesState := aesWait

      /*
       * Invoke Memory state machine
       */
      memLevel    := lv_l2
      memOrder(0) := lv_l1
      memOrder(1) := lv_l0
      memOrder(2) := lv_cr
      memOrder(3) := lv_mem  // load mem before Tag to calculate CWMAC earlier
      memOrder(4) := lv_tag
      memOrderIdx := 0.U
      memRW       := rwRead
      memState    := memIssue

      // Invalidate all cachelines
      cachelineValid.foreach(_ := false.B)

      /*
       * Invoke CWMAC state machine
       */
      cwmacLevel     := lv_l2   // Traverse order is always L2 -> L1 -> L0 -> CR -> Tag(Mem)
      cwmacTagsEqual := true.B
      cwmacState     := cwmacVerifyIssue

      printf_dbg("  Tree::Tree accept\n")
      printf_dbg("      rw: %d\n", mrw_t);
      printf_dbg("      id: %x\n", mid_t);
      printf_dbg("    addr: %x\n", maddr_t);
      printf_dbg("    data: %x\n", mdata_t.asUInt)
      printf_dbg("    root: %x\n", io.root)

    }
    // end of ``when (treeState === treeIdle)''

  }
  when (treeState === treeVerify) {
    // Invoke CWMAC/Memory/AES State Machines
    treeState := treeComp
    printf_dbg("  Tree::Tree verify\n")

  }
  when (treeState === treeComp) {
    // Waiting CWMAC/Memory State Machines to verify (compare tags)

    when (cipherValid & cwmacTagsCompleted) {
      // Completion of cwmac implies that all cachelines have been loaded
      resp := Cat(rwState, verified)

      // when read, all process were done.
      // when write, update tree, then release
      //   when detected integrity error, DO NOT UPDATE
      treeState := Mux(((rwState === rwRead) | !verified), treeRelease, treeUpdate)

      printf_dbg("  Tree::Tree compare\n")
      printf_dbg("    verified: %d\n", verified)
      when ((rwState === rwWrite) & !verified) {
        printf_dbg("    *** DETECTED INTEGRITY ERROR!!! ***\n")
      }
    }
    // end of ``while (treeState === treeComp)''

  }
  when (treeState === treeUpdate) {
    // update tree
    // increase each nonce
    val rootInc_t = increment(root)
    val l2Inc_t   = increment(cacheline(lv_l2).nonce(nonceIdx(lv_l2)))
    val l1Inc_t   = increment(cacheline(lv_l1).nonce(nonceIdx(lv_l1)))
    val l0Inc_t   = increment(cacheline(lv_l0).nonce(nonceIdx(lv_l0)))
    val crInc_t   = increment(cacheline(lv_cr).nonce(nonceIdx(lv_cr)))

    root := rootInc_t
    cacheline(lv_l2).nonce(nonceIdx(lv_l2)) := l2Inc_t
    cacheline(lv_l1).nonce(nonceIdx(lv_l1)) := l1Inc_t
    cacheline(lv_l0).nonce(nonceIdx(lv_l0)) := l0Inc_t
    cacheline(lv_cr).nonce(nonceIdx(lv_cr)) := crInc_t
    cacheline(lv_mem) := (data ^ cipher).asTypeOf(new CacheLine)

    treeState := treeRelease

    /*
     * Invoke Memory state machine
     */
    memLevel    := lv_mem
    memOrder(0) := lv_tag
    memOrder(1) := lv_cr
    memOrder(2) := lv_l0
    memOrder(3) := lv_l1
    memOrder(4) := lv_l2
    memOrderIdx := 0.U
    memRW       := rwWrite
    memState    := memIssue

    /*
     * Invoke CWMAC state machine
     */
    cwmacLevel := lv_tag   // Traverse order is always L2 -> L1 -> L0 -> CR -> Tag(Mem)
    cwmacState := cwmacUpdateIssue
    for (i <- 0 to 4) { cachelineValid(i) := false.B }


    printf_dbg("  Tree::Tree update\n");
    printf_dbg("      root: %x -> %x\n", root, rootInc_t)
    printf_dbg("        L2: %x -> %x\n", cacheline(lv_l2).nonce(nonceIdx(lv_l2)), l2Inc_t)
    printf_dbg("        L1: %x -> %x\n", cacheline(lv_l1).nonce(nonceIdx(lv_l1)), l1Inc_t)
    printf_dbg("        L0: %x -> %x\n", cacheline(lv_l0).nonce(nonceIdx(lv_l0)), l0Inc_t)
    printf_dbg("        CR: %x -> %x\n", cacheline(lv_cr).nonce(nonceIdx(lv_cr)), crInc_t)

  }
  when (treeState === treeRelease) {
    // put response on Frontend Slave

    // when has established handshake, go to idle
    treeState := Mux(io.fe.slave.ready, treeIdle, treeRelease)
  }


  /** Increment counter on GF(2^56) */
  def increment(cntr: UInt): UInt = {
    // m55 = -cntr(55)
    // if cntr(55) = 1, -1 (0xFFFFFF...)
    // if cntr(55) = 0, 0
    val m55 = Mux(cntr(55), "xFFFFFFFFFFFFFF".U(56.W), 0.U(56.W))
    ((cntr << 1) ^ ("x80000C00000001".U(56.W) & m55))(55, 0)
  }

  def printf_dbg(fmt: String, data: Bits*) {
    when (debug) {
      printf.apply(Printable.pack(fmt, data:_*))
    }
  }

}



/** object to generate verilog
  */
object TreeDriver extends App {
  Driver.emitVerilog(new Tree)
}
