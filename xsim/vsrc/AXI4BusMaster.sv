// See LICENSE for license details.
module AXI4BusMaster
 #(
   parameter IDBITS = 4,
   parameter DATABITS = 64,
   parameter NAME = ""
   )
   (
    input                   clock,
    input                   reset,

    // AR
    output                  arvalid,
    input                   arready,
    output [IDBITS-1:0]     arid,
    output [31:0]           araddr,
    output [7:0]            arlen,
    output [2:0]            arsize,
    output [1:0]            arburst,
    output                  arlock,
    output [3:0]            arcache,
    output [2:0]            arprot,
    output [3:0]            arqos,

    // AW
    output                  awvalid,
    input                   awready,
    output [IDBITS-1:0]     awid,
    output [31:0]           awaddr,
    output [7:0]            awlen,
    output [2:0]            awsize,
    output [1:0]            awburst,
    output                  awlock,
    output [3:0]            awcache,
    output [2:0]            awprot,
    output [3:0]            awqos,

    // R
    input                   rvalid,
    output                  rready,
    input [IDBITS-1:0]      rid,
    input [DATABITS-1:0]    rdata,
    input [1:0]             rresp,
    input                   rlast,

    // W
    output                  wvalid,
    input                   wready,
    output [DATABITS-1:0]   wdata,
    output [DATABITS/8-1:0] wstrb,
    output                  wlast,

    // B
    input                   bvalid,
    output                  bready,
    input [IDBITS-1:0]      bid,
    input [1:0]             bresp,
    );

   // fix some ports
   localparam BEATS     = 512/DATABITS;
   localparam BEATS_M1  = BEATS - 1;
   localparam DATABYTES = DATABITS/8;

   assign arlen   = BEATS_M1[7:0];
   assign arsize  = $clog2(DATABITS/8);
   assign arburst = 2'b00;
   assign arlock  = 1'b0;
   assign arcache = 4'b0011;
   assign arprot  = 3'b001;
   assign arqos   = 4'b0000;

   assign awlen   = BEATS_M1[7:0];
   assign awsize  = $clog2(DATABITS/8);
   assign awburst = 2'b00;
   assign awlock  = 1'b0;
   assign awcache = 4'b0011;
   assign awprot  = 3'b001;
   assign awqos   = 4'b0000;

   assign wstrb   = {DATABYTES{1'b1}};


   reg                 arvalid_r;
   reg                 awvalid_r;
   reg                 rready_r;
   reg                 wvalid_r;
   reg [DATABYTES-1:0] wdata_r;
   reg                 wlast_r;
   reg                 bready_r;
   assign arvalid = arvalid_r;
   assign awvalid = awvalid_r;
   assign rready  = rready_r;
   assign wvalid  = wvalid_r;
   assign wdata   = wdata_r;
   assign wlast   = wlast_r;
   assign bready  = bready_r;


   // buffer
   reg [DATABYTES-1:0] rdata_i_idx;
   reg [511:0]         rdata_i;
   reg [DATABYTES-1:0] wdata_i_idx;
   reg [511:0]         wdata_i;



   // states
   enum { AR_IDLE, AR_BUSY }           ar_state;
   enum {  R_IDLE,  R_BUSY,  R_READY }  r_state;
   enum { AW_IDLE, AW_BUSY,  W_BUSY }  aw_state;
   enum {  B_IDLE,  B_READY, B_BUSY }   b_state;



   // test cases as queue
   logic [31:0] test_r[$];

   







  // state machine
   always_ff @( posedge clock )
   begin
      if ( rst )
      begin
         arvalid_r <= 1'b0;
         awvalid_r <= 1'b0;
         wvalid_r  <= 1'b0;
         rready_r  <= 1'b0;
         bready_r  <= 1'b0;

         ar_state <= AR_IDLE;
         r_state  <= R_IDLE;
         aw_state <= AW_IDLE;
         b_state  <= B_IDLE;
      end // if ( rst )
      else
      begin
         /*
          * AR transition
          */
         if ( ar_state == AR_IDLE )
         begin
            // if can issue, issue the next test case
            // to be implemented
         end
         else if ( ar_state == AR_BUSY )
         begin
            if ( arvalid & arready )
            begin
               arvalid  <= 1'b0;
               ar_state <= AR_IDLE;

               $display("AXI4BusMaster::AR::Established\n");
            end
         end


         /*
          * R transition
          */
         if ( r_state == R_IDLE )
         begin
            // wait for slave response
            if ( rvalid )
            begin
               rready _r <= 1'b1;

               // get rdata
               rdata_i[0*DATABITS +: DATABITS] <= rdata;
               rdata_i_idx <= 'd1;
               r_state     <= rlast ? R_READY : R_BUSY;
            end
         end // if ( r_state == R_IDLE )
         else if ( r_state == R_BUSY )
         begin
            // under data beats
            rdata_i[rdata_idx*DATABITS +: DATABITS] <= rdata;
            rdata_i_idx <= rdata_i_idx + 'd1;
            r_state     <= rlast ? R_READY : R_BUSY;
         end
         else if ( r_state == R_READY )
         begin
            // assertion
            // to be implemented
            rready_r <= 1'b0;
            


         end


         /*
          * AW/W Transition
          */
         if ( aw_state == AW_IDLE )
         begin
            // can issue next request
            // to be implemented

         end
         else if ( aw_state == AW_BUSY )
         begin
            // wait for AW handshake
            if ( awvalid & awready )
            begin
               awvalid_r <= 1'b0;
               aw_state  <= W_BUSY;

               // Put data on W bus
               wvalid_r    <= 1'b1;
               wdata_r     <= wdata_i[0*DATABITS +: DATABITS];
               wdata_i_idx <= 'd1;
               wlast       <= (0 == BEATS_M1) ? 1'b1 : 1'b0;
            end
         end
         else if ( aw_state == W_BUSY )
         begin
            // under W handshake
            if ( wvalid & wready )
            begin
               wdata_r     <= wdata_i[0*DATABITS +: DATABITS];
               wdata_i_idx <= wdata_i_idx + 'd1;
               wlast       <= (wdata_i_idx == BEATS_M1) ? 1'b1 : 1'b0;

               wvalid_r    <= wlast ? 1'b0 : 1'b1;
               aw_state    <= wlast ? AW_IDLE : W_BUSY;
            end
         end















      end // else: !if( rst )
   end // always_ff @ ( posedge clock )

endmodule // AXI4BusMaster
