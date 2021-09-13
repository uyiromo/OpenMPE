// See LICENSE for license details.
package openmpe

import chisel3._
import chisel3.util._
import chisel3.util.Enum
import chisel3.stage.ChiselStage
import chisel3.util.experimental._
import chisel3.experimental.chiselName
import chisel3.util.experimental.BoringUtils

/** A module for Carter-Wegman MAC
  */
class CWMACOptIn extends Bundle {
  val addr  = UInt(26.W)
  val nonce = UInt(56.W)
  val msg   = UInt(448.W)
}


// CWMACOpt Top In/Out
class CWMACOptIO extends Bundle {
  val source  = Flipped(Decoupled(new CWMACOptIn))
  val tag     = Decoupled(UInt(56.W))

  val keyEnc  = Input(UInt(128.W))  // Encryption Key
  val keyHash = Input(UInt(512.W))  // Hash Key
}


/**
  * Compute Carter-Wegman MAC (Top Module)
  */
class CWMACOpt extends MultiIOModule {

  // Top Module I/O
  val io = IO(new CWMACOptIO)

  // Input buffers
  val msg = Reg(UInt(512.W))
  val msg64x8 = WireDefault(msg.asTypeOf(Vec(8, UInt(64.W))))
  val key64x8 = WireDefault(io.keyHash.asTypeOf(Vec(8, UInt(64.W))))

  // verbose output
  //val debug = true.B
  val debug = false.B

  // GMulOpt Pipeline
  val regM = Reg(UInt(64.W))    // source M
  val regK = Reg(UInt(64.W))    // source K
  val gmul  = Module(new GMulOpt())
  gmul.io.m := regM
  gmul.io.k := regK

  // GModOpt Pipeline
  val regMK = Reg(UInt(128.W))     // source MK (the result of gmul(M, K))
  val gmod  = Module(new GModOpt())
  gmod.io.mult  := regMK

  // AES128 Module
  val aes = Module(new AES128Opt(1))
  aes.io.plain.bits.addr  := io.source.bits.addr
  aes.io.plain.bits.nonce := io.source.bits.nonce
  aes.io.plain.valid      := io.source.valid
  aes.io.key              := io.keyEnc

  // GF (GMulOpt/Mod) states
  val stage = RegInit(0.U(4.W))
  val acc   = Reg(UInt(64.W))

  // io.tag
  io.tag.bits  := (acc ^ aes.io.cipher(63, 0))(55, 0)
  io.tag.valid := aes.io.cipherValid  // AES is slower than GF(2^56)

  /*
   *
   *  GF(2^56) state machine
   *
   */
  /*
   * *** Pipeline ***
   * | stage |  Mul   | Mod/Acc |
   * |   0   | Set(7) |         |
   * |   1   | Set(6) | Set(7)  |
   * |   2   | Set(5) | Set(6)  |
   * |   3   | Set(4) | Set(5)  |
   * |   4   | Set(3) | Set(4)  |
   * |   5   | Set(2) | Set(3)  |
   * |   6   | Set(1) | Set(2)  |
   * |   7   | Set(0) | Set(1)  |
   * |   8   |        | Set(0)  |
   */
  io.source.nodeq()
  when (stage === 0.U(4.W)) {
    // can accept new requests now

    regMK := 0.U(128.W)
    acc   := 0.U(64.W)
    when (io.source.valid) {
      val source = io.source.deq()
      msg := Cat(0.U(64.W), source.msg)

      // start pipeline
      val msg_t = source.msg(63, 0)
      val key_t = key64x8(0)

      regM  := msg_t
      regK  := key_t
      stage := 1.U(4.W)

      printf_dbg("  CWMACOpt::stage(%d)\n", stage)
      printf_dbg("    M: %x\n", msg_t)
      printf_dbg("    K: %x\n", key_t)
    }
  } .elsewhen (stage === 9.U(4.W)) {
    // GF completed, waiting handshake
    when (io.tag.ready) {
      stage := 0.U(4.W)
    }

  } .otherwise {
    // pipeline
    acc   := acc ^ gmod.io.mod
    regM  := msg64x8(stage)
    regK  := key64x8(stage)
    regMK := gmul.io.mult
    stage := stage + 1.U(4.W)

    // debug
    printf_dbg("  CWMACOpt::stage(%d)\n", stage)
    printf_dbg("      M: %x\n", msg64x8(stage))
    printf_dbg("      K: %x\n", key64x8(stage))
    printf_dbg("     MK: %x\n", gmul.io.mult)
    printf_dbg("    Acc: %x\n", acc)
  }



  /** debug print */
  def printf_dbg(fmt: String, data: Bits*) {
    when (debug) {
      printf.apply(Printable.pack(fmt, data:_*))
    }
  }
}


class GMulOpt extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val m     = Input(UInt(64.W))
    val k     = Input(UInt(64.W))
    val mult  = Output(UInt(128.W))
  })

  setInline("GMulOpt.v",
    s"""
       |module GMulOpt(
       |  input  [ 63:0] m,
       |  input  [ 63:0] k,
       |  output [127:0] mult
       |);
       |
       |integer i;
       |reg [127:0] mult_r;
       |
       |always @*
       |begin
       |  mult_r = 128'b0;
       |  for (i = 0; i < 64; i = i + 1)
       |    if (k[i]) mult_r = mult_r ^ (m << i);
       |end
       |
       |assign mult = mult_r;
       |endmodule
       |""".stripMargin)
}

class GModOpt extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val mult  = Input(UInt(128.W))
    val mod   = Output(UInt(64.W))
  })

  setInline("GModOpt.v",
    s"""
       |module GModOpt(
       |  input  [127:0] mult,
       |  output [ 63:0] mod
       |);
       |
       |integer i;
       |reg [127:0] mod_r;
       |
       |always @*
       |begin
       |  mod_r = mult;
       |  for (i = 127; i >= 64; i = i - 1)
       |    if (mod_r[i]) mod_r = mod_r ^ (65'h1000000000000001B << (i - 64));
       |end
       |
       |assign mod = mod_r[63:0];
       |endmodule
       |""".stripMargin)
}


/** object to generate verilog
  */
object CWMACOptDriver extends App {
  Driver.emitVerilog(new CWMACOpt)
  //(new chisel3.stage.ChiselStage).emitFirrtl(new CWMACOpt)
  //(new chisel3.stage.ChiselStage).emitVerilog(new CWMACOpt)
}
