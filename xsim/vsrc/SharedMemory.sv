// See LICENSE for license details.
module SharedMemory();
/* -----\/----- EXCLUDED -----\/-----
   logic [511:0]        memory[logic[31:0]];

   function [511:0] mem_read(input logic [31:0] addr);
      mem_read = memory.exists(addr) ? memory[addr] : 'hx;
   endfunction

   function void mem_write(input logic [31:0] addr, input logic [511:0] data);
      memory[addr] = data;
   endfunction
 -----/\----- EXCLUDED -----/\----- */

endmodule // SharedMemory
