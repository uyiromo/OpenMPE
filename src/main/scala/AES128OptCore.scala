// See LICENSE for license details.
package nvsit

import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage
import chisel3.util.experimental._
import chisel3.experimental.chiselName
import chisel3.util.experimental.BoringUtils


/** A core module of AES128
  * AES-128 is implemented based on Aoki Lab., Tohoku Univ.
  * In this file, it is called as ``Aoki-AES''
  * http://www.aoki.ecei.tohoku.ac.jp/crypto/web/cores.html
  * http://www.aoki.ecei.tohoku.ac.jp/crypto/items/AES.v
  */

// AES-128 Core In/Out
class AES128OptCoreIO extends Bundle
{
  val stateIn  = Input(UInt(128.W))  // source state
  val keyKE    = Input(UInt(128.W))  // source key (expanded)
  val stateOut = Output(UInt(128.W)) // result state
  val round    = Input(UInt(4.W))    // current stage
}


/** Compute AES-128 encryption (Core Module)
  */
class AES128OptCore extends MultiIOModule {

  val io      = IO(new AES128OptCoreIO)
  val stateSB = Wire(UInt(128.W))  // result of SubBytes
  val stateSR = Wire(UInt(128.W))  // result of ShiftRows
  val stateMC = Wire(UInt(128.W))  // result of MixColumns
  val stateAK = Wire(UInt(128.W))  // result of AddRoundKey

  // verbose output
  //val debug = true.B
  val debug = false.B

  printf_dbg("  AES128OptCore::Input\n")
  printf_dbg("    stateIn: %x\n", io.stateIn)
  printf_dbg("      keyIn: %x\n", io.keyKE)

  // 1. SubBytes
  stateSB := Cat(
    subBytes(io.stateIn(127, 96)),
    subBytes(io.stateIn( 95, 64)),
    subBytes(io.stateIn( 63, 32)),
    subBytes(io.stateIn( 31,  0))
  )
  printf_dbg("  AES128OptCore::SubBytes\n")
  printf_dbg("    stateSB: %x\n", stateSB)

  // 2. ShiftRows
  stateSR := shiftRows(stateSB)
  printf_dbg("  AES128OptCore::ShiftRows\n")
  printf_dbg("    stateSR: %x\n", stateSR)

  // 3. MixColumns
  stateMC := Cat(
    mixColumns(stateSR(127, 96)),
    mixColumns(stateSR( 95, 64)),
    mixColumns(stateSR( 63, 32)),
    mixColumns(stateSR( 31,  0))
  )
  printf_dbg("  AES128OptCore::MixColumns\n")
  printf_dbg("    stateMC: %x\n", stateMC)

  // This AES128 module, get ``round-N key'' in ``round-N'' simultaneously

  // 4. AddRoundKey
  //    if final round(10), bypass mixColumns
  stateAK := io.keyKE ^ (Mux(io.round === 0xA.U, stateSR, stateMC))
  printf_dbg("  AES128OptCore::AddRoundKey\n")
  printf_dbg("    stateAK: %x\n", stateAK)

  // output
  io.stateOut := stateAK

  printf_dbg("  AES128OptCore::Input\n")
  printf_dbg("    stateOut: %x\n", stateAK)






  def printf_dbg(fmt: String, data: Bits*) {
    when (debug) {
      printf.apply(Printable.pack(fmt, data:_*))
    }
  }

  /*
   *
   *  End of class procedure body
   *
   */
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


  /** Shift each row
    *
    * This function corresponds with ``assign sr = ...'' in Aoki-AES
    *
    * @param state / UInt(128.W)
    *         source state
    * @return shifted state as UInt(128.W)
    */
  @chiselName
  def shiftRows(state: UInt): UInt = {
    /*
     * [  0,  4,  8, 12 ]                   [  0,  4,  8, 12 ]
     * [  1,  5,  9, 13 ]     shiftRows     [  5,  9, 13,  1 ]
     * [  2,  6, 10, 14 ]   ------------->  [ 10, 14,  2,  6 ]
     * [  3,  7, 11, 15 ]                   [ 15,  3,  7, 11 ]
     */

    val byte00 = state(127, 120)
    val byte01 = state(119, 112)
    val byte02 = state(111, 104)
    val byte03 = state(103,  96)
    val byte04 = state( 95,  88)
    val byte05 = state( 87,  80)
    val byte06 = state( 79,  72)
    val byte07 = state( 71,  64)
    val byte08 = state( 63,  56)
    val byte09 = state( 55,  48)
    val byte10 = state( 47,  40)
    val byte11 = state( 39,  32)
    val byte12 = state( 31,  24)
    val byte13 = state( 23,  16)
    val byte14 = state( 15,   8)
    val byte15 = state(  7,   0)

    Cat(
      byte00, byte05, byte10, byte15,
      byte04, byte09, byte14, byte03,
      byte08, byte13, byte02, byte07,
      byte12, byte01, byte06, byte11
    )
  }


  /** Mix each column
    *
    * This function corresponds with ``MixColumns'' in Aoki-AES
    *
    * @param state / UInt(32.W)
    *         source state
    * @return converted state as UInt(32.W)
    */
  @chiselName
  def mixColumns(state: UInt): UInt = {
    val a3 = state(31, 24)
    val a2 = state(23, 16)
    val a1 = state(15,  8)
    val a0 = state( 7,  0)

    val b3 = a3 ^ a2
    val b2 = a2 ^ a1
    val b1 = a1 ^ a0
    val b0 = a0 ^ a3

    Cat(
      a2(7) ^ b1(7) ^ b3(6),
      a2(6) ^ b1(6) ^ b3(5),
      a2(5) ^ b1(5) ^ b3(4),
      a2(4) ^ b1(4) ^ b3(3) ^ b3(7),
      a2(3) ^ b1(3) ^ b3(2) ^ b3(7),
      a2(2) ^ b1(2) ^ b3(1),
      a2(1) ^ b1(1) ^ b3(0) ^ b3(7),
      a2(0) ^ b1(0) ^ b3(7),
      a3(7) ^ b1(7) ^ b2(6),
      a3(6) ^ b1(6) ^ b2(5),
      a3(5) ^ b1(5) ^ b2(4),
      a3(4) ^ b1(4) ^ b2(3) ^ b2(7),
      a3(3) ^ b1(3) ^ b2(2) ^ b2(7),
      a3(2) ^ b1(2) ^ b2(1),
      a3(1) ^ b1(1) ^ b2(0) ^ b2(7),
      a3(0) ^ b1(0) ^ b2(7),
      a0(7) ^ b3(7) ^ b1(6),
      a0(6) ^ b3(6) ^ b1(5),
      a0(5) ^ b3(5) ^ b1(4),
      a0(4) ^ b3(4) ^ b1(3) ^ b1(7),
      a0(3) ^ b3(3) ^ b1(2) ^ b1(7),
      a0(2) ^ b3(2) ^ b1(1),
      a0(1) ^ b3(1) ^ b1(0) ^ b1(7),
      a0(0) ^ b3(0) ^ b1(7),
      a1(7) ^ b3(7) ^ b0(6),
      a1(6) ^ b3(6) ^ b0(5),
      a1(5) ^ b3(5) ^ b0(4),
      a1(4) ^ b3(4) ^ b0(3) ^ b0(7),
      a1(3) ^ b3(3) ^ b0(2) ^ b0(7),
      a1(2) ^ b3(2) ^ b0(1),
      a1(1) ^ b3(1) ^ b0(0) ^ b0(7),
      a1(0) ^ b3(0) ^ b0(7)
    )
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
/*@chiselName
  def keyExpansion(key: UInt, round: UInt): UInt = {
    val rcon    = Wire(UInt(8.W))
    val keySB   = Wire(UInt(32.W))
    val keyOut0 = Wire(UInt(32.W))
    val keyOut1 = Wire(UInt(32.W))
    val keyOut2 = Wire(UInt(32.W))
    val keyOut3 = Wire(UInt(32.W))

    // choose rcon
    rcon := MuxLookup(round, 0x36.U,  // default case corresponds with 0xA
      Seq(
        0x1.U -> 0x01.U,
        0x2.U -> 0x02.U,
        0x3.U -> 0x04.U,
        0x4.U -> 0x08.U,
        0x5.U -> 0x10.U,
        0x6.U -> 0x20.U,
        0x7.U -> 0x40.U,
        0x8.U -> 0x80.U,
        0x9.U -> 0x1b.U
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
    Cat(key_out0, key_out1, key_out2, key_out3)
  }*/

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
