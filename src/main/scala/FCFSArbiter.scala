// See LICENSE for license details.
package nvsit

import scala.math.pow

import chisel3._
import chisel3.util._
import chisel3.util.Enum
import chisel3.stage.ChiselStage
import chisel3.util.experimental._
import chisel3.experimental.chiselName
import chisel3.util.experimental.BoringUtils

/**
  * Round Robin Arbiter with skipping invalid masters
  */
class FCFSArbiter(numValid: Int) extends BlackBox with HasBlackBoxInline {
  val numValidWidth = log2Up(numValid)
  val queueNum      = pow(2, numValidWidth).asInstanceOf[Int]

  val io = IO(new Bundle {
    val clock   = Input(Bool())
    val reset   = Input(Bool())
    val dequeue = Input(Bool())
    val empty   = Output(Bool())
    //val valids  = Input(Vec(numValid, Bool()))
    val valids  = Input(UInt(numValid.W))
    val choice  = Output(UInt(numValidWidth.W))
  })

  setInline("FCFSArbiter.v",
    s"""
       |module FCFSArbiter(
       |   input  clock,
       |   input  reset,
       |   input  dequeue,
       |   output empty,
       |   input  [${numValid-1}:0]     valids,
       |   output [${numValidWidth-1}:0] choice
       |);
       |
       |// numInputBits per word, numInput words
       |// one master can issue one valid at a time
       |reg [${numValidWidth-1}:0] choiceQueue[${queueNum-1}:0];
       |reg [${numValidWidth-1}:0] nextEnqIdx;
       |reg [${numValidWidth-1}:0] nextDeqIdx;
       |reg [${numValid-1}:0]      validsChecked;
       |
       |integer i;
       |always @( posedge clock )
       |begin
       |  if (reset)
       |  begin
       |     nextEnqIdx    = 'd0;
       |     nextDeqIdx    = 'd0;
       |     validsChecked = 'h0;
       |  end
       |  else
       |  begin
       |     for (i = 0; i < ${numValid}; i = i + 1)
       |     begin
       |        if (valids[i] & !validsChecked[i])
       |        begin
       |           choiceQueue[nextEnqIdx] = i[${numValidWidth-1}:0];
       |           nextEnqIdx              = nextEnqIdx + 'd1;
       |           validsChecked[i]        = 1'b1;
       |        end
       |     end
       |     if (dequeue)
       |     begin
       |        nextDeqIdx            = nextDeqIdx + 'd1;
       |        validsChecked[choice] = 1'b0;
       |     end
       |
       |  end
       |end
       |
       |assign empty  = (nextEnqIdx == nextDeqIdx);
       |assign choice = choiceQueue[nextDeqIdx];
       |
       |endmodule
       |""".stripMargin)
}
