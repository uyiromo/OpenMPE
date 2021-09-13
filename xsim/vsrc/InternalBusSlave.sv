// See LICENSE for license details.
`timescale 1ns/1ps

module InternalBusSlave
 #(
   parameter IDBITS = 4,
   parameter NAME = ""
   )
   (
    input               clock,
    input               reset,

    // master ports
    input               mvalid,
    output              mready,
    input [IDBITS-1:0]  mid,
    input [31:0]        maddr,
    input [511:0]       mdata,
    input               mrw,

   // slave ports
    output              svalid,
    input               sready,
    output [IDBITS-1:0] sid,
    output [1:0]        sresp,
    output [511:0]      sdata,
    output              srw
    );

   reg                  mready_r;
   reg                  svalid_r;
   reg [IDBITS-1:0]     sid_r;
   reg [1:0]            sresp_r;
   reg [511:0]          sdata_r;
   reg                  srw_r;

   assign mready = mready_r;
   assign svalid = svalid_r;
   assign sid    = sid_r;
   assign sresp  = sresp_r;
   assign sdata  = sdata_r;
   assign srw    = srw_r;

   // buffer
   reg [IDBITS-1:0]     id;
   reg [31:0]           addr;
   reg                  rw;

   // memory control
   localparam MEMORY_LATENCY = 30;  // as clock
   reg [31:0]           memory_wait_counter;
   //logic [511:0]        memory[logic[31:0]];
   //byte        memory[int unsigned];

   // states
   enum { IDLE, MEMORY_BUSY, SLAVE_BUSY } state;


   // state machine
   always_ff @( posedge clock )
   begin
      if ( reset )
      begin
         mready_r <= 1'b0;
         svalid_r <= 1'b0;
         state    <= IDLE;
      end
      else
      begin
         if ( state == IDLE )
         begin
            // can accept handshake on master bus
            if ( mvalid )
            begin
               mready_r <= 1'b1;
               id       <= mid;
               addr     <= maddr;
               rw       <= mrw;

               memory_wait_counter <= MEMORY_LATENCY - 'd1;
               state <= MEMORY_BUSY;

               if (!mrw)
                 mem_write(maddr, mdata);
                 //memory[maddr] = mdata;

               $display("  InternalBusSlave::%s::IDLE", NAME);
               $display("      id: %d",   mid);
               $display("    addr: %08X", maddr);
               $display("    data: %8X",  mdata);
               $display("      rw: %b",   mrw);
            end // if ( mvalid )
         end // if ( state == IDLE )

         else if ( state == MEMORY_BUSY )
         begin
            mready_r <= 1'b0;

            // while memory latency
            memory_wait_counter <= memory_wait_counter - 'd1;

            if (memory_wait_counter == 0)
            begin
               // can return data
               sid_r    <= id;
               //sdata_r  <= memory.exists(addr) ? memory[addr] : 'hx;
               sdata_r  <= mem_read(addr);
               sresp_r  <= 2'b00;
               srw_r    <= rw;
               svalid_r <= 1'b1;

               state <= SLAVE_BUSY;

               $display("  InternalBusSlave::%s::MEMORY_BUSY", NAME);
               $display("      id: %d",  id);
               //$display("    data: %8X", memory.exists(addr) ? memory[addr] : 'hx);
               $display("    data: %8X", mem_read(addr));
               $display("    resp: %d",  2'b00);
               $display("      rw: %b",  rw);
            end // if (memory_wait_counter == 0)
         end // if ( state == MEMORY_BUSY )

         else if ( state == SLAVE_BUSY )
         begin
            if ( svalid & sready )
            begin
               svalid_r <= 1'b0;
               state    <= IDLE;

               $display("  InternalBusSlave::%s::SLAVE_BUSY", NAME);
            end
         end

      end // else: !if( rst )
   end // always_ff @ ( posedge clock )

endmodule // InternalBusSlave
