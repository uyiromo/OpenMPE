`timescale 1ns/1ps

module AXI4BusSlave
 #(
   // parameters
   parameter IDBITS    = 4,
   parameter DATABITS  = 512,
   parameter NAME      = ""
    )
   (
    // clock/reset
    input logic                  clock,
    input logic                  reset,

    // AXI4 Channels
    // AR
    input  logic                 arvalid,
    output logic                 arready,
    input  logic [IDBITS-1:0]    arid,
    input  logic [31:0]          araddr,
    input  logic [7:0]           arlen,
    input  logic [2:0]           arsize,

    // AW
    input  logic                 awvalid,
    output logic                 awready,
    input  logic [IDBITS-1:0]    awid,
    input  logic [31:0]          awaddr,
    input  logic [7:0]           awlen,
    input  logic [2:0]           awsize,

    // R
    output logic                 rvalid,
    input  logic                 rready,
    output logic [IDBITS-1:0]    rid,
    output logic [DATABITS-1:0]  rdata,
    output logic [1:0]           rresp,
    output logic                 rlast,

    // W
    input  logic                  wvalid,
    output logic                  wready,
    input  logic [DATABITS-1:0]   wdata,
    input  logic [DATABITS/8-1:0] wstrb,
    input  logic                  wlast,

    // B
    output logic                  bvalid,
    input  logic                  bready,
    output logic [IDBITS-1:0]     bid,
    output logic [1:0]            bresp
    );

   localparam BEATS    = 512/DATABITS;
   localparam BEATS_M1 = BEATS - 'd1;

   // buffers
   logic [IDBITS-1:0]             arid_r;
   logic [31:0]                   araddr_r;
   logic [511:0]                  rdata_r;
   logic [3:0]                    rdata_r_idx;
   logic [IDBITS-1:0]             awid_r;
   logic [31:0]                   awaddr_r;
   logic [511:0]                  wdata_r;
   logic [3:0]                    wdata_r_idx;



   // states
   enum { R_IDLE, R_WAIT, R_BUSY } read_state;
   enum { W_IDLE, W_BUSY, W_WAIT, B_BUSY } write_state;

   localparam READ_LATENCY  = 10;  // read latency as clock
   localparam WRITE_LATENCY = 10;  // write latency as clock
   logic [31:0] read_wait_counter;
   logic [31:0] write_wait_counter;



   // state machine
   assign arready = (read_state == R_IDLE);
   assign rvalid  = (read_state == R_BUSY);
   assign rdata   = rdata_r[rdata_r_idx*DATABITS +: DATABITS];
   assign rlast   = (rdata_r_idx == BEATS_M1);
   assign rresp   = 2'b00;
   assign rid     = arid_r;

   assign awready = (write_state == W_IDLE);
   assign wready  = (write_state == W_BUSY);
   assign bvalid  = (write_state == B_BUSY);
   assign bresp   = 2'b00;
   assign bid     = awid_r;

   always_ff @( posedge clock )
   begin
      if ( reset )
      begin
         read_state  <= R_IDLE;
         write_state <= W_IDLE;
      end
      else
      begin
         /*
          * Read Transition
          */
         if ( read_state == R_IDLE )
         begin
            // waiting for ARvalid
            if ( arvalid )
            begin
               arid_r     <= arid;
               araddr_r   <= araddr;
               read_state <= R_WAIT;

               // set read latency counter
               read_wait_counter <= READ_LATENCY - 'd1;

               //$strobe("  AXI4BusSlave::R_IDLE");
               //$strobe("      arid: %d", arid_r);
               //$strobe("    araddr: %X", araddr_r);
            end // if ( arvalid )
         end // if ( read_state == R_IDLE )
         else if ( read_state == R_WAIT )
         begin
            read_wait_counter <= read_wait_counter - 'd1;

            // waiting for read latency
            if ( read_wait_counter == 'd0 )
            begin
               rdata_r     <= mem_read(araddr_r);
               rdata_r_idx <= 'd0;
               read_state  <= R_BUSY;
            end
         end // if ( read_state == R_WAIT )
         else if ( read_state == R_BUSY )
         begin
            if ( rready )
            begin
               rdata_r_idx <= rdata_r_idx + 'd1;
               read_state  <= rlast ? R_IDLE : R_BUSY;

               //$strobe("  AXI4BusSlave::R_BUSY");
               //$strobe("    rdata: %X", rdata);
               //$strobe("    rdata_r_idx: %d", rdata_r_idx - 'd1);
            end
         end // if ( read_state == R_BUSY )





         /*
          * Write Transition
          */
         if ( write_state == W_IDLE )
         begin
            // waiting for AWvalid
            if ( awvalid )
            begin
               awid_r      <= awid;
               awaddr_r    <= awaddr;
               write_state <= W_BUSY;
               wdata_r_idx <= 1'd0;

               //$strobe("  AXI4BusSlave::W_IDLE");
               //$strobe("      awid: %d", awid_r);
               //$strobe("    awaddr: %d", awaddr_r);
            end // if ( awvalid )
         end // if ( write_state == W_IDLE )
         else if ( write_state == W_BUSY )
         begin
            // W channel data beats
            if ( wvalid )
            begin
               wdata_r[wdata_r_idx*DATABITS +: DATABITS] <= wdata;
               wdata_r_idx <= wdata_r_idx + 'd1;
               write_state <= wlast ? W_WAIT : W_BUSY;

               if (wlast)
                  write_wait_counter <= WRITE_LATENCY - 'd1;

               //$strobe("  AXI4BusSlave::W_BUSY");
               //$strobe("    wdata: %X", wdata_r);
               //$strobe("    wdata_r_idx: %d", wdata_r_idx - 'd1);
            end
         end // if ( write_state == W_BUSY )
         else if ( write_state == W_WAIT )
         begin
            // waiting for MEMORY LATENCY
            write_wait_counter <= write_wait_counter - 'd1;
            if ( write_wait_counter == 'd0 )
            begin
               mem_write(awaddr_r, wdata_r);
               write_state <= B_BUSY;
               //$strobe("  AXI4BusSlave::W_WAIT");
            end
         end
         else if ( write_state == B_BUSY )
         begin
            if ( bready )
            begin
               write_state <= W_IDLE;

               //$strobe("  AXI4BusSlave::B_BUSY");
               //$strobe("    All done...");
            end
         end // if ( write_state == B_BUSY )
      end // else: !if( reset )
   end // always_ff @ ( posedge clock )
endmodule // AXI4BusSlave
