// See LICENSE for license details.
`timescale 1ns/1ps

`include "Connection.sv"

module tb_Frontend();

   // parameters
   parameter IDBITS    = 4;
   parameter DATABITS  = 64;
   parameter NAME      = "CPU";
   parameter BEATS     = 512/DATABITS;
   parameter BEATS_M1  = BEATS - 1;
   parameter DATABYTES = DATABITS/8;

   // clock/reset
   logic clock;
   logic reset;

   // AXI4 Channels
   // AR
   logic                arvalid;
   logic                arready;
   logic [IDBITS-1:0]   arid;
   logic [31:0]         araddr;
   logic [7:0]          arlen;
   logic [2:0]          arsize;
   logic [1:0]          arburst;
   logic                arlock;
   logic [3:0]          arcache;
   logic [2:0]          arprot;
   logic [3:0]          arqos;

   // AW
   logic                awvalid;
   logic                awready;
   logic [IDBITS-1:0]   awid;
   logic [31:0]         awaddr;
   logic [7:0]          awlen;
   logic [2:0]          awsize;
   logic [1:0]          awburst;
   logic                awlock;
   logic [3:0]          awcache;
   logic [2:0]          awprot;
   logic [3:0]          awqos;

   // R
   logic                rvalid;
   logic                rready;
   logic [IDBITS-1:0]   rid;
   logic [DATABITS-1:0] rdata;
   logic [1:0]          rresp;
   logic                rlast;

   // W
   logic                  wvalid;
   logic                  wready;
   logic [DATABITS-1:0]   wdata;
   logic [DATABITS/8-1:0] wstrb;
   logic                  wlast;

   // B
   logic                  bvalid;
   logic                  bready;
   logic [IDBITS-1:0]     bid;
   logic [1:0]            bresp;

   // fix some ports
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

   // buffer
   logic [DATABYTES-1:0] rdata_r_idx;
   logic [511:0]         rdata_r;
   logic [DATABYTES-1:0] wdata_r_idx;
   logic [511:0]         wdata_r;

   // response buffers
   logic [IDBITS-1:0]  bid_r;
   logic [1:0]         bresp_r;
   logic [IDBITS-1:0]  rid_r;
   logic [1:0]         rresp_r;

   assign wlast   = (wdata_r_idx == BEATS_M1);
   assign wstrb   = {DATABYTES{1'b1}};

   // DUT
   `TREE(0);
   `TREE(1);
   `TREE(2);
   `TREE(3);
   `TREE(4);
   `TREE(5);
   `TREE(6);
   `TREE(7);
   `BACKEND();
   `FRONTEND();


   // states
   enum { AR_IDLE, AR_BUSY }           ar_state;
   enum {  R_IDLE,  R_BUSY, R_READY }   r_state;
   enum { AW_IDLE, AW_BUSY, W_BUSY }   aw_state;
   enum {  B_IDLE,  B_READY }           b_state;


   // memory
   logic [511:0]        memory[logic[31:0]];

   function [511:0] mem_read(input logic [31:0] addr);
      mem_read = memory.exists(addr) ? memory[addr] : 'hx;
   endfunction

   function void mem_write(input logic [31:0] addr, input logic [511:0] data);
      memory[addr] = data;
   endfunction



   /*
    * Clock Generation
    */
   parameter CLK = 2;
   logic [63:0] time_counter;
   logic [63:0] cycle_counter;
   logic [63:0] prev_size_r;
   logic [63:0] prev_size_w;
   logic [63:0] prev_issued_cc;
   always #(CLK/2)
   begin
     clock <= !clock;
     time_counter <= time_counter + 'd1;
   end

   always @*
   begin
      @( posedge clock );
      $display("current_clock: %0d", cycle_counter);
      cycle_counter <= cycle_counter + 'd1;
   end

   /*
    * Test cases
    */
   logic [31:0] read_queue[$];                  // test case FIFO
   logic [31:0] write_queue[$];                 // test case FIFO
   logic [31:0] id_addr_map[logic[IDBITS-1:0]]; // issued requests map (key: id, data: cacheline)
   logic [511:0] expect_rdata;


   logic [ 3:0]  next_arid;
   logic [ 3:0]  next_awid;
   logic [31:0] next_araddr;
   logic [31:0] next_awaddr;


   parameter RW_THRESHOLD = 4;
   logic        allow_ar;
   logic        allow_aw;
   logic [3:0]  issued_counter;
   logic [3:0]  next_issued_counter;
   assign next_issued_counter = issued_counter + 'd1;

   localparam KiB = 64'd1024;
   localparam MiB = 64'd1024*1024;
   localparam GiB = 64'd1024*1024*1024;

   // address map in DRAM
   localparam PM_BEGIN   = 64'h40000000;
   localparam PM_END     = PM_BEGIN + 96*MiB;

   /*
    * Loop
    */
   initial
   begin
      // enable waveform dump
      $dumpfile("tb_Frontend.vcd");
      $dumpvars(0, tb_Frontend);

      // generate test cases
      for (longint addr = PM_BEGIN; addr < PM_END; addr = addr + MiB)
      begin
         read_queue.push_back(addr[31:0]);
         write_queue.push_back(addr[31:0]);
         //read_queue.push_back(PM_BEGIN[31:0]);
         //write_queue.push_back(PM_BEGIN[31:0]);
      end

      // initialize
      clock           <= 1'b0;
      allow_aw        <= 1'b1;
      allow_ar        <= 1'b0;
      issued_counter  <= 'd0;
      next_arid       <= 4'h0;
      next_awid       <= 4'h0;
      cycle_counter   <= 'd0;
      time_counter    <= 'd0;
      prev_issued_cc  <= 'd0;
      prev_size_r     <= 'd0;
      prev_size_w     <= 'd0;

      // main loop
      fork
         tickAR();
         tickR();
         tickAW();
         tickB();
      join_none

      // reset
      reset <= 1'b1;
      #20;
      reset <= 1'b0;

      // max simulation time [ns]
      while (time_counter < 3000000)
      begin
         @( posedge clock );

         // idle detection
         if ( (cycle_counter - prev_issued_cc) > 500 )
         begin
            $display("Idle cycle exceeds 500 clocks !!!");
            $finish;

         end

         if ( (read_queue.size() != prev_size_r) | (write_queue.size() != prev_size_w))
           prev_issued_cc <= cycle_counter;

         prev_size_r <= read_queue.size();
         prev_size_w <= write_queue.size();


         if ((read_queue.size() == 0) & (write_queue.size() == 0))
         begin
            $display("All tests passed");
            $finish;
         end
      end
      //#300000;

      $display("  Max Simulation Time passed");
      $finish;
   end // initial begin



   /*
    * Tasks
    */

   // tick AR channel
   task tickAR();
      begin
         forever
         begin
            @( posedge clock );
            if ( reset )
            begin
               ar_state <= AR_IDLE;
               arvalid  <= 1'b0;
            end
            else
            begin
               if ( ar_state == AR_IDLE )
               begin
                  // if can, issue next request
                  if ( (allow_ar) & (read_queue.size() != 0) )
                  begin
                     allow_ar  <= 1'b0;

                     arvalid   <= 1'b1;
                     araddr    <= read_queue[0];
                     arid      <= next_arid;
                     next_arid <= next_arid + 4'd1;

                     // register relation between id/addr
                     id_addr_map[next_arid] = read_queue[0];

                     $display("  AXI4BusMaster::AR::Issue");
                     $display("    araddr: %08X", read_queue[0]);
                     $display("      arid: %d",   next_arid);

                     ar_state <= AR_BUSY;
                     read_queue.pop_front();

                  end // if ( (doing_rw == DOING_READ) & (read_queue.size() != 0) )
               end // if ( ar_state == AR_IDLE )
               else if ( ar_state == AR_BUSY )
               begin
                  // under AR handshake
                  if ( arvalid & arready )
                  begin
                     arvalid  <= 1'b0;
                     ar_state <= AR_IDLE;

                     $display("  AXI4Busmaster::AR::Established");
                  end
               end // if ( ar_state == AR_BUSY )
            end // else: !if( reset )
         end // forever begin
      end
   endtask // tickAR


   // tick R channel
   task tickR();
      begin
         forever
         begin
            @( posedge clock );
            if ( reset )
            begin
               r_state <= R_IDLE;
               rready  <= 1'b0;
            end
            else
            begin
               if ( r_state == R_IDLE )
               begin
                  // waiting for slave handshake
                  if ( rvalid )
                  begin
                     rready      <= 1'b1;
                     rdata_r_idx <= 1'b0;
                     r_state     <= R_BUSY;

                     rid_r       <= rid;
                     rresp_r     <= rresp;

                     $display("  AXI4BusMaster::R::rdata[%0d]: %X",
                              0, rdata);
                  end // if ( rvalid )
               end // if ( r_state == R_IDLE )
               else if ( r_state == R_BUSY )
               begin
                  // under data beats
                  rdata_r[rdata_r_idx*DATABITS +: DATABITS] <= rdata;
                  rdata_r_idx <= rdata_r_idx + 'd1;
                  rready      <= rlast ? 1'b0 : 1'b1;
                  r_state     <= rlast ? R_READY : R_BUSY;

                  $display("  AXI4BusMaster::R::rdata[%0d]: %X",
                           rdata_r_idx, rdata);
               end
               else if ( r_state == R_READY )
               begin
                  r_state <= R_IDLE;
                  $display("  AXI4BusMaster::R::rdata_r: %X\n", rdata_r);

                  // assertion
                  expect_rdata = {480'h0, id_addr_map[rid_r]};
                  rresp: assert( rresp_r == 2'h00 ) $display("  AXI4BusMaster::R::Resp/Validated");
                     else $fatal(0, "AXI4BusMaster::R::Resp/ERROR (expected: %d, returned: %d)",
                                 2'h00, rresp_r);

                  rdata:
                    assert( rdata_r == expect_rdata ) $display("  AXI4BusMaster::R::Data/Validated");
                     else $fatal(0, "AXI4BusMaster::R::Data/ERROR (expected: %X, returned: %X)",
                                 expect_rdata, rdata_r);

                  // allow AR or AW
                  if ( next_issued_counter == RW_THRESHOLD )
                  begin
                     issued_counter <= 'd0;
                     allow_aw       <= 1'b1;
                     allow_ar       <= 1'b0;
                  end
                  else
                  begin
                     issued_counter <= next_issued_counter;
                     allow_aw       <= 1'b0;
                     allow_ar       <= 1'b1;
                  end

               end // if ( r_state == R_READY )
            end // else: !if( reset )
         end // forever begin
      end
   endtask // tickR


   // tick AW channel
   task tickAW();
      begin
         forever
         begin
            @( posedge clock );
            if ( reset )
            begin
               aw_state <= AW_IDLE;
               awvalid  <= 1'b0;
            end
            else
            begin
               if ( aw_state == AW_IDLE )
               begin
                  // if can, issue next request
                  if ( (allow_aw) & (write_queue.size() != 0) )
                  begin
                     allow_aw   <= 1'b0;

                     awvalid     <= 1'b1;
                     wvalid      <= 1'b0;
                     wdata_r_idx <= 1'b0;

                     awaddr    <= write_queue[0];
                     awid      <= next_awid;
                     wdata_r   <= {480'h0, write_queue[0]};
                     next_awid <= next_awid + 4'd1;

                     $display("  AXI4BusMaster::AW::Issue");
                     $display("    awaddr: %08X", write_queue[0]);
                     $display("      awid: %d",   next_awid);
                     $display("     wdata: %X",   {480'h0, write_queue[0]});

                     aw_state <= AW_BUSY;
                     write_queue.pop_front();
                  end // if ( (doing_rw == DOING_WRITE) & (write_queue.size() != 0) )
               end // if ( aw_state == AW_IDLE )
               else if ( aw_state == AW_BUSY )
               begin
                  // under AW handshake
                  if ( awvalid & awready )
                  begin
                     awvalid  <= 1'b0;
                     //aw_state <= W_BUSY;

                     $display("  AXI4Busmaster::AW::Established");

                     // Put data on W bus
                     wvalid      <= 1'b1;
                     wdata       <= wdata_r[wdata_r_idx*DATABITS +: DATABITS];
                     aw_state    <= W_BUSY;

                     $display("  AXI4BusMaster::W::wdata[%0d]: %X",
                              wdata_r_idx, wdata_r[wdata_r_idx*DATABITS +: DATABITS]);
                  end // if ( awvalid & awready )
               end // if ( aw_state == AW_BUSY )
               else if ( aw_state == W_BUSY )
               begin
                  if ( wvalid & wready )
                  begin
                     // under data beats
                     wvalid      <= wlast ? 1'b0 : 1'b1;
                     wdata       <= wdata_r[(wdata_r_idx+'d1)*DATABITS +: DATABITS];
                     wdata_r_idx <= wdata_r_idx + 'd1;
                     aw_state    <= wlast ? AW_IDLE : W_BUSY;

                     $display("  AXI4BusMaster::W::wdata[%0d]: %X",
                              wdata_r_idx, wdata_r[(wdata_r_idx+'d1)*DATABITS +: DATABITS]);

                     if ( wlast )
                     begin
                        // allow AR or AW
                        if ( next_issued_counter == RW_THRESHOLD )
                        begin
                           issued_counter <= 'd0;
                           allow_aw       <= 1'b0;
                           allow_ar       <= 1'b1;
                        end
                        else
                        begin
                           issued_counter <= next_issued_counter;
                           allow_aw       <= 1'b1;
                           allow_ar       <= 1'b0;
                        end
                     end

                  end
               end // if ( aw_state == W_BUSY )
            end // else: !if( reset )
         end // forever begin
      end
   endtask // tickAW


   // tick B channel
   task tickB();
      begin
         forever
         begin
            @( posedge clock );
            if ( reset )
            begin
               b_state <= B_IDLE;
               bready  <= 1'b0;
            end
            else
            begin
               if ( b_state == B_IDLE )
               begin
                  // waiting for slave handshake
                  if ( bvalid )
                  begin
                     bready  <= 1'b1;
                     bid_r   <= bid;
                     bresp_r <= bresp;
                     b_state <= B_READY;

                  end
               end // if ( b_state == B_IDLE )
               else if ( b_state == B_READY )
               begin
                  b_state <= B_IDLE;

                  // assertion
                  bresp: assert( bresp_r == 2'h00 ) $display("  AXI4BusMaster::B::Validated");
                     else $fatal(0, "AXI4BusMaster::B::ERROR (expected: %d, returned: %d)",
                                 2'h00, bid_r);

               end
            end // else: !if( reset )
         end // forever begin
      end
   endtask // tickB





endmodule // tb_Frontend





























//
