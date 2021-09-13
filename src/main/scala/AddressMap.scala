// See LICENSE for license details.
package nvsit

import chisel3._
import chisel3.util._
import chisel3.stage.ChiselStage
import chisel3.util.experimental._
import chisel3.experimental.chiselName
import chisel3.util.experimental.BoringUtils

/** Global address map
  * 4-GiB DRAM (head 1-GiB is DRAM, other 3-GiB is NVMM)
  * Address Map:
  * 0x00000000 - 0x3FFFFFFF -> DRAM
  * 0x40000000 - 0xFFFFFFFF -> NVMM
  *
  * A part of MIG converts RAW memory addresses to DRAM memory addresses
  * MPE can get 32-bit address
  */
object AddressMap {
  //val DRAM_BEGIN = 0x00000000L.U(32.W)
  //val DRAM_END   = 0x3FFFFFFFL.U(32.W)
  //val NVMM_BEGIN = 0x40000000L.U(32.W)
  //val NVMM_END   = 0xFFFFFFFFL.U(32.W)
  //val PM_BEGIN   = 0x40000000L.U(32.W)
  //val PM_END     = 0x46000000L.U(32.W) // 96-MiB
  val DRAM_BEGIN = 0x0000000L.U(26.W)
  val DRAM_END   = 0x0FFFFFFL.U(26.W)
  val NVMM_BEGIN = 0x1000000L.U(26.W)
  val NVMM_END   = 0x3FFFFFFL.U(26.W)
  val PM_BEGIN   = NVMM_BEGIN
  val PM_END     = 0x1180000L.U(26.W) // 96-MiB
}
