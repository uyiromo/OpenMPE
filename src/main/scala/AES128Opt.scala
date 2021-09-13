// See LICENSE for license details.
package openmpe

import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage
import chisel3.util.experimental._
import chisel3.experimental.chiselName
import chisel3.util.experimental.BoringUtils


/** A wrapper and control module of AES128Core
  * AES-128 is implemented based on Aoki Lab., Tohoku Univ.
  * In this file, it is called as ``Aoki-AES''
  * http://www.aoki.ecei.tohoku.ac.jp/crypto/web/cores.html
  * http://www.aoki.ecei.tohoku.ac.jp/crypto/items/AES.v
  *
  * This AES128 module is optimized for SGX-style Integrity Tree
  */

// Integrity Tree does not require full-width (128 bits) input
// can build full-width plain from addr and nonce
class AES128OptIn extends Bundle {
  val addr  = UInt(26.W)
  val nonce = UInt(56.W)
}

// AES-128 Top In/Out
class AES128OptIO(cipherBits: Int) extends Bundle {
  val plain       = Flipped(Decoupled(new AES128OptIn))
  val key         = Input(UInt(128.W))         // Encryption Key
  val cipher      = Output(UInt(cipherBits.W)) // Encrypted Plain (Cipher)
  val cipherValid = Output(Bool())

  override def cloneType = (new AES128OptIO(cipherBits)).asInstanceOf[this.type]
}

/**
  * Compute AES-128 encryption (Top Module)
  *
  * @param parallel
  *          the number of parallel AES128 modules
  *          AES128Opt can calculate ``128*(parallel)'' bits at a time
  */
class AES128Opt(parallel: Int) extends MultiIOModule {

  // Top Module I/O
  val cipherBits = 128 * parallel
  val io = IO(new AES128OptIO(cipherBits))

  // internal registers
  val state = Reg(Vec(parallel, UInt(128.W)))
  val keyKE = Reg(UInt(128.W))  // result key of keyExpansion
  val round = Reg(UInt(4.W))    // current round
  val busy  = RegInit(false.B)  // module is busy or not

  // verbose output
  //val debug = true.B
  val debug = false.B

  // instantiate AES128Core
  val coreIO = Seq.fill(parallel) { Module(new AES128OptCore).io }
  for (i <- 0 to parallel - 1) {
    coreIO(i).stateIn := state(i)
    coreIO(i).keyKE   := keyKE
    coreIO(i).round   := round
  }
  io.cipher      := state.asTypeOf(UInt(cipherBits.W))
  io.cipherValid := (round === 0xB.U(4.W))

  // state machine
  io.plain.nodeq()
  when (!busy) {
    when (io.plain.valid) {
      // Accept input and build initial state
      val plain_t = io.plain.deq()
      for (i <- 0 to parallel - 1) {
        val state_t = (0.U(128.W) | (plain_t.addr << 58) | (i.U(2.W) << 56) | plain_t.nonce)
        state(i) := state_t ^ io.key

        printf_dbg("  AES128Opt::Accept(%d)\n", i.U(2.W))
        printf_dbg("    state: %x\n", state_t)
      }

      // Initialize
      keyKE := keyExpansion(io.key, 0x0.U(4.W))
      round := 0x1.U(4.W)
      busy  := true.B
    }
  } .otherwise {
    // when busy
    printf_dbg("  AES128Opt::round(%d)\n", round)
    printf_dbg("    keyKE: %x\n", keyKE)

    for (i <- 0 to parallel - 1) {
      state(i) := coreIO(i).stateOut
      printf_dbg("    state(%d): %x\n", i.U, coreIO(i).stateOut)
    }
    round := round + 1.U(4.W)
    keyKE := keyExpansion(keyKE, round)

    busy := (round =/= 0xA.U(4.W))
  }

  /////////////////////////////////////////////////////////////////////
  def printf_dbg(fmt: String, data: Bits*) {
    when (debug) {
      printf.apply(Printable.pack(fmt, data:_*))
    }
  }

  /** Calculate next key
    *
    * This function corresponds with ``function rcon ... assign ko'' in Aoki-AES
    *
    * @param key / UInt(128.W)
    *          round key
    * @param round / UInt(10.W)
    *          current round bit-array
    * @return next round key as UInt(128.W)
    */
  @chiselName
  def keyExpansion(key: UInt, round: UInt): UInt = {
    val rcon    = Wire(UInt(8.W))
    val keySB   = Wire(UInt(32.W))
    val keyOut0 = Wire(UInt(32.W))
    val keyOut1 = Wire(UInt(32.W))
    val keyOut2 = Wire(UInt(32.W))
    val keyOut3 = Wire(UInt(32.W))

    // choose rcon
    // shift 1 bits because need to get next round key
    rcon := MuxLookup(round, 0x36.U,  // default case corresponds with 0x9
      Seq(
        0x0.U -> 0x01.U,
        0x1.U -> 0x02.U,
        0x2.U -> 0x04.U,
        0x3.U -> 0x08.U,
        0x4.U -> 0x10.U,
        0x5.U -> 0x20.U,
        0x6.U -> 0x40.U,
        0x7.U -> 0x80.U,
        0x8.U -> 0x1b.U
      )
    )

    // subBytes
    keySB := subBytes(
      Cat(key(23, 16), key(15, 8), key(7,0), key(31, 24))
    )

    // xor
    keyOut0 := key(127, 96) ^ Cat((keySB(31, 24) ^ rcon), keySB(23, 0))
    keyOut1 := key( 95, 64) ^ keyOut0
    keyOut2 := key( 63, 32) ^ keyOut1
    keyOut3 := key( 31,  0) ^ keyOut2

    // return
    Cat(keyOut0, keyOut1, keyOut2, keyOut3)
  }

  /** Convert each byte by using SBox
    *
    * This function corresponds with ``SubBytes'' in Aoki-AES
    *
    * @param state / UInt(32.W)
    *         source state
    * @return converted state as UInt(32.W)
    */
  @chiselName
  def subBytes(state: UInt): UInt = {
    val s0 = state(31, 24)
    val s1 = state(23, 16)
    val s2 = state(15,  8)
    val s3 = state( 7,  0)

    Cat(
      sbox(s0),
      sbox(s1),
      sbox(s2),
      sbox(s3)
    )
  }


  def sbox(idx: UInt): UInt = {
    MuxLookup(idx, 0x00.U(8.W),
      Array(
        0x00.U(8.W) -> 0x63.U(8.W),
        0x01.U(8.W) -> 0x7c.U(8.W),
        0x02.U(8.W) -> 0x77.U(8.W),
        0x03.U(8.W) -> 0x7b.U(8.W),
        0x04.U(8.W) -> 0xf2.U(8.W),
        0x05.U(8.W) -> 0x6b.U(8.W),
        0x06.U(8.W) -> 0x6f.U(8.W),
        0x07.U(8.W) -> 0xc5.U(8.W),
        0x08.U(8.W) -> 0x30.U(8.W),
        0x09.U(8.W) -> 0x01.U(8.W),
        0x0A.U(8.W) -> 0x67.U(8.W),
        0x0B.U(8.W) -> 0x2b.U(8.W),
        0x0C.U(8.W) -> 0xfe.U(8.W),
        0x0D.U(8.W) -> 0xd7.U(8.W),
        0x0E.U(8.W) -> 0xab.U(8.W),
        0x0F.U(8.W) -> 0x76.U(8.W),
        0x10.U(8.W) -> 0xca.U(8.W),
        0x11.U(8.W) -> 0x82.U(8.W),
        0x12.U(8.W) -> 0xc9.U(8.W),
        0x13.U(8.W) -> 0x7d.U(8.W),
        0x14.U(8.W) -> 0xfa.U(8.W),
        0x15.U(8.W) -> 0x59.U(8.W),
        0x16.U(8.W) -> 0x47.U(8.W),
        0x17.U(8.W) -> 0xf0.U(8.W),
        0x18.U(8.W) -> 0xad.U(8.W),
        0x19.U(8.W) -> 0xd4.U(8.W),
        0x1A.U(8.W) -> 0xa2.U(8.W),
        0x1B.U(8.W) -> 0xaf.U(8.W),
        0x1C.U(8.W) -> 0x9c.U(8.W),
        0x1D.U(8.W) -> 0xa4.U(8.W),
        0x1E.U(8.W) -> 0x72.U(8.W),
        0x1F.U(8.W) -> 0xc0.U(8.W),
        0x20.U(8.W) -> 0xb7.U(8.W),
        0x21.U(8.W) -> 0xfd.U(8.W),
        0x22.U(8.W) -> 0x93.U(8.W),
        0x23.U(8.W) -> 0x26.U(8.W),
        0x24.U(8.W) -> 0x36.U(8.W),
        0x25.U(8.W) -> 0x3f.U(8.W),
        0x26.U(8.W) -> 0xf7.U(8.W),
        0x27.U(8.W) -> 0xcc.U(8.W),
        0x28.U(8.W) -> 0x34.U(8.W),
        0x29.U(8.W) -> 0xa5.U(8.W),
        0x2A.U(8.W) -> 0xe5.U(8.W),
        0x2B.U(8.W) -> 0xf1.U(8.W),
        0x2C.U(8.W) -> 0x71.U(8.W),
        0x2D.U(8.W) -> 0xd8.U(8.W),
        0x2E.U(8.W) -> 0x31.U(8.W),
        0x2F.U(8.W) -> 0x15.U(8.W),
        0x30.U(8.W) -> 0x04.U(8.W),
        0x31.U(8.W) -> 0xc7.U(8.W),
        0x32.U(8.W) -> 0x23.U(8.W),
        0x33.U(8.W) -> 0xc3.U(8.W),
        0x34.U(8.W) -> 0x18.U(8.W),
        0x35.U(8.W) -> 0x96.U(8.W),
        0x36.U(8.W) -> 0x05.U(8.W),
        0x37.U(8.W) -> 0x9a.U(8.W),
        0x38.U(8.W) -> 0x07.U(8.W),
        0x39.U(8.W) -> 0x12.U(8.W),
        0x3A.U(8.W) -> 0x80.U(8.W),
        0x3B.U(8.W) -> 0xe2.U(8.W),
        0x3C.U(8.W) -> 0xeb.U(8.W),
        0x3D.U(8.W) -> 0x27.U(8.W),
        0x3E.U(8.W) -> 0xb2.U(8.W),
        0x3F.U(8.W) -> 0x75.U(8.W),
        0x40.U(8.W) -> 0x09.U(8.W),
        0x41.U(8.W) -> 0x83.U(8.W),
        0x42.U(8.W) -> 0x2c.U(8.W),
        0x43.U(8.W) -> 0x1a.U(8.W),
        0x44.U(8.W) -> 0x1b.U(8.W),
        0x45.U(8.W) -> 0x6e.U(8.W),
        0x46.U(8.W) -> 0x5a.U(8.W),
        0x47.U(8.W) -> 0xa0.U(8.W),
        0x48.U(8.W) -> 0x52.U(8.W),
        0x49.U(8.W) -> 0x3b.U(8.W),
        0x4A.U(8.W) -> 0xd6.U(8.W),
        0x4B.U(8.W) -> 0xb3.U(8.W),
        0x4C.U(8.W) -> 0x29.U(8.W),
        0x4D.U(8.W) -> 0xe3.U(8.W),
        0x4E.U(8.W) -> 0x2f.U(8.W),
        0x4F.U(8.W) -> 0x84.U(8.W),
        0x50.U(8.W) -> 0x53.U(8.W),
        0x51.U(8.W) -> 0xd1.U(8.W),
        0x52.U(8.W) -> 0x00.U(8.W),
        0x53.U(8.W) -> 0xed.U(8.W),
        0x54.U(8.W) -> 0x20.U(8.W),
        0x55.U(8.W) -> 0xfc.U(8.W),
        0x56.U(8.W) -> 0xb1.U(8.W),
        0x57.U(8.W) -> 0x5b.U(8.W),
        0x58.U(8.W) -> 0x6a.U(8.W),
        0x59.U(8.W) -> 0xcb.U(8.W),
        0x5A.U(8.W) -> 0xbe.U(8.W),
        0x5B.U(8.W) -> 0x39.U(8.W),
        0x5C.U(8.W) -> 0x4a.U(8.W),
        0x5D.U(8.W) -> 0x4c.U(8.W),
        0x5E.U(8.W) -> 0x58.U(8.W),
        0x5F.U(8.W) -> 0xcf.U(8.W),
        0x60.U(8.W) -> 0xd0.U(8.W),
        0x61.U(8.W) -> 0xef.U(8.W),
        0x62.U(8.W) -> 0xaa.U(8.W),
        0x63.U(8.W) -> 0xfb.U(8.W),
        0x64.U(8.W) -> 0x43.U(8.W),
        0x65.U(8.W) -> 0x4d.U(8.W),
        0x66.U(8.W) -> 0x33.U(8.W),
        0x67.U(8.W) -> 0x85.U(8.W),
        0x68.U(8.W) -> 0x45.U(8.W),
        0x69.U(8.W) -> 0xf9.U(8.W),
        0x6A.U(8.W) -> 0x02.U(8.W),
        0x6B.U(8.W) -> 0x7f.U(8.W),
        0x6C.U(8.W) -> 0x50.U(8.W),
        0x6D.U(8.W) -> 0x3c.U(8.W),
        0x6E.U(8.W) -> 0x9f.U(8.W),
        0x6F.U(8.W) -> 0xa8.U(8.W),
        0x70.U(8.W) -> 0x51.U(8.W),
        0x71.U(8.W) -> 0xa3.U(8.W),
        0x72.U(8.W) -> 0x40.U(8.W),
        0x73.U(8.W) -> 0x8f.U(8.W),
        0x74.U(8.W) -> 0x92.U(8.W),
        0x75.U(8.W) -> 0x9d.U(8.W),
        0x76.U(8.W) -> 0x38.U(8.W),
        0x77.U(8.W) -> 0xf5.U(8.W),
        0x78.U(8.W) -> 0xbc.U(8.W),
        0x79.U(8.W) -> 0xb6.U(8.W),
        0x7A.U(8.W) -> 0xda.U(8.W),
        0x7B.U(8.W) -> 0x21.U(8.W),
        0x7C.U(8.W) -> 0x10.U(8.W),
        0x7D.U(8.W) -> 0xff.U(8.W),
        0x7E.U(8.W) -> 0xf3.U(8.W),
        0x7F.U(8.W) -> 0xd2.U(8.W),
        0x80.U(8.W) -> 0xcd.U(8.W),
        0x81.U(8.W) -> 0x0c.U(8.W),
        0x82.U(8.W) -> 0x13.U(8.W),
        0x83.U(8.W) -> 0xec.U(8.W),
        0x84.U(8.W) -> 0x5f.U(8.W),
        0x85.U(8.W) -> 0x97.U(8.W),
        0x86.U(8.W) -> 0x44.U(8.W),
        0x87.U(8.W) -> 0x17.U(8.W),
        0x88.U(8.W) -> 0xc4.U(8.W),
        0x89.U(8.W) -> 0xa7.U(8.W),
        0x8A.U(8.W) -> 0x7e.U(8.W),
        0x8B.U(8.W) -> 0x3d.U(8.W),
        0x8C.U(8.W) -> 0x64.U(8.W),
        0x8D.U(8.W) -> 0x5d.U(8.W),
        0x8E.U(8.W) -> 0x19.U(8.W),
        0x8F.U(8.W) -> 0x73.U(8.W),
        0x90.U(8.W) -> 0x60.U(8.W),
        0x91.U(8.W) -> 0x81.U(8.W),
        0x92.U(8.W) -> 0x4f.U(8.W),
        0x93.U(8.W) -> 0xdc.U(8.W),
        0x94.U(8.W) -> 0x22.U(8.W),
        0x95.U(8.W) -> 0x2a.U(8.W),
        0x96.U(8.W) -> 0x90.U(8.W),
        0x97.U(8.W) -> 0x88.U(8.W),
        0x98.U(8.W) -> 0x46.U(8.W),
        0x99.U(8.W) -> 0xee.U(8.W),
        0x9A.U(8.W) -> 0xb8.U(8.W),
        0x9B.U(8.W) -> 0x14.U(8.W),
        0x9C.U(8.W) -> 0xde.U(8.W),
        0x9D.U(8.W) -> 0x5e.U(8.W),
        0x9E.U(8.W) -> 0x0b.U(8.W),
        0x9F.U(8.W) -> 0xdb.U(8.W),
        0xA0.U(8.W) -> 0xe0.U(8.W),
        0xA1.U(8.W) -> 0x32.U(8.W),
        0xA2.U(8.W) -> 0x3a.U(8.W),
        0xA3.U(8.W) -> 0x0a.U(8.W),
        0xA4.U(8.W) -> 0x49.U(8.W),
        0xA5.U(8.W) -> 0x06.U(8.W),
        0xA6.U(8.W) -> 0x24.U(8.W),
        0xA7.U(8.W) -> 0x5c.U(8.W),
        0xA8.U(8.W) -> 0xc2.U(8.W),
        0xA9.U(8.W) -> 0xd3.U(8.W),
        0xAA.U(8.W) -> 0xac.U(8.W),
        0xAB.U(8.W) -> 0x62.U(8.W),
        0xAC.U(8.W) -> 0x91.U(8.W),
        0xAD.U(8.W) -> 0x95.U(8.W),
        0xAE.U(8.W) -> 0xe4.U(8.W),
        0xAF.U(8.W) -> 0x79.U(8.W),
        0xB0.U(8.W) -> 0xe7.U(8.W),
        0xB1.U(8.W) -> 0xc8.U(8.W),
        0xB2.U(8.W) -> 0x37.U(8.W),
        0xB3.U(8.W) -> 0x6d.U(8.W),
        0xB4.U(8.W) -> 0x8d.U(8.W),
        0xB5.U(8.W) -> 0xd5.U(8.W),
        0xB6.U(8.W) -> 0x4e.U(8.W),
        0xB7.U(8.W) -> 0xa9.U(8.W),
        0xB8.U(8.W) -> 0x6c.U(8.W),
        0xB9.U(8.W) -> 0x56.U(8.W),
        0xBA.U(8.W) -> 0xf4.U(8.W),
        0xBB.U(8.W) -> 0xea.U(8.W),
        0xBC.U(8.W) -> 0x65.U(8.W),
        0xBD.U(8.W) -> 0x7a.U(8.W),
        0xBE.U(8.W) -> 0xae.U(8.W),
        0xBF.U(8.W) -> 0x08.U(8.W),
        0xC0.U(8.W) -> 0xba.U(8.W),
        0xC1.U(8.W) -> 0x78.U(8.W),
        0xC2.U(8.W) -> 0x25.U(8.W),
        0xC3.U(8.W) -> 0x2e.U(8.W),
        0xC4.U(8.W) -> 0x1c.U(8.W),
        0xC5.U(8.W) -> 0xa6.U(8.W),
        0xC6.U(8.W) -> 0xb4.U(8.W),
        0xC7.U(8.W) -> 0xc6.U(8.W),
        0xC8.U(8.W) -> 0xe8.U(8.W),
        0xC9.U(8.W) -> 0xdd.U(8.W),
        0xCA.U(8.W) -> 0x74.U(8.W),
        0xCB.U(8.W) -> 0x1f.U(8.W),
        0xCC.U(8.W) -> 0x4b.U(8.W),
        0xCD.U(8.W) -> 0xbd.U(8.W),
        0xCE.U(8.W) -> 0x8b.U(8.W),
        0xCF.U(8.W) -> 0x8a.U(8.W),
        0xD0.U(8.W) -> 0x70.U(8.W),
        0xD1.U(8.W) -> 0x3e.U(8.W),
        0xD2.U(8.W) -> 0xb5.U(8.W),
        0xD3.U(8.W) -> 0x66.U(8.W),
        0xD4.U(8.W) -> 0x48.U(8.W),
        0xD5.U(8.W) -> 0x03.U(8.W),
        0xD6.U(8.W) -> 0xf6.U(8.W),
        0xD7.U(8.W) -> 0x0e.U(8.W),
        0xD8.U(8.W) -> 0x61.U(8.W),
        0xD9.U(8.W) -> 0x35.U(8.W),
        0xDA.U(8.W) -> 0x57.U(8.W),
        0xDB.U(8.W) -> 0xb9.U(8.W),
        0xDC.U(8.W) -> 0x86.U(8.W),
        0xDD.U(8.W) -> 0xc1.U(8.W),
        0xDE.U(8.W) -> 0x1d.U(8.W),
        0xDF.U(8.W) -> 0x9e.U(8.W),
        0xE0.U(8.W) -> 0xe1.U(8.W),
        0xE1.U(8.W) -> 0xf8.U(8.W),
        0xE2.U(8.W) -> 0x98.U(8.W),
        0xE3.U(8.W) -> 0x11.U(8.W),
        0xE4.U(8.W) -> 0x69.U(8.W),
        0xE5.U(8.W) -> 0xd9.U(8.W),
        0xE6.U(8.W) -> 0x8e.U(8.W),
        0xE7.U(8.W) -> 0x94.U(8.W),
        0xE8.U(8.W) -> 0x9b.U(8.W),
        0xE9.U(8.W) -> 0x1e.U(8.W),
        0xEA.U(8.W) -> 0x87.U(8.W),
        0xEB.U(8.W) -> 0xe9.U(8.W),
        0xEC.U(8.W) -> 0xce.U(8.W),
        0xED.U(8.W) -> 0x55.U(8.W),
        0xEE.U(8.W) -> 0x28.U(8.W),
        0xEF.U(8.W) -> 0xdf.U(8.W),
        0xF0.U(8.W) -> 0x8c.U(8.W),
        0xF1.U(8.W) -> 0xa1.U(8.W),
        0xF2.U(8.W) -> 0x89.U(8.W),
        0xF3.U(8.W) -> 0x0d.U(8.W),
        0xF4.U(8.W) -> 0xbf.U(8.W),
        0xF5.U(8.W) -> 0xe6.U(8.W),
        0xF6.U(8.W) -> 0x42.U(8.W),
        0xF7.U(8.W) -> 0x68.U(8.W),
        0xF8.U(8.W) -> 0x41.U(8.W),
        0xF9.U(8.W) -> 0x99.U(8.W),
        0xFA.U(8.W) -> 0x2d.U(8.W),
        0xFB.U(8.W) -> 0x0f.U(8.W),
        0xFC.U(8.W) -> 0xb0.U(8.W),
        0xFD.U(8.W) -> 0x54.U(8.W),
        0xFE.U(8.W) -> 0xbb.U(8.W),
        0xFF.U(8.W) -> 0x16.U(8.W),
      )
    )
  }
}


/** object to generate verilog
  */
object AES128OptDriver extends App {
  Driver.emitVerilog(new AES128Opt(4))
  //(new chisel3.stage.ChiselStage).emitFirrtl(new AES128Opt(4))
  //(new chisel3.stage.ChiselStage).emitVerilog(new AES128Opt(4))
}
