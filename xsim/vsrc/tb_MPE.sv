`timescale 1ns/1ps

//`include "Connection.sv"

module tb_MPE();

   // clock/reset
   logic clock;
   logic reset;

   parameter C_IDBITS   = 4;
   parameter C_DATABITS = 64;
   parameter M_IDBITS   = 4;
   parameter M_DATABITS = 512;

   localparam M_AXLEN = 512/M_DATABITS - 1;
   localparam M_AXSIZE = $clog2(M_DATABITS/8);


   /*
    * CPU Wires
    */
   logic                  io_cpu_ar_valid;
   logic                  io_cpu_ar_ready;
   logic [C_IDBITS-1:0]   io_cpu_ar_bits_id;
   logic [25:0]           io_cpu_ar_bits_addr;

   logic                  io_cpu_aw_valid;
   logic                  io_cpu_aw_ready;
   logic [C_IDBITS-1:0]   io_cpu_aw_bits_id;
   logic [25:0]           io_cpu_aw_bits_addr;

   logic                  io_cpu_r_valid;
   logic                  io_cpu_r_ready;
   logic [C_IDBITS-1:0]   io_cpu_r_bits_id;
   logic [C_DATABITS-1:0] io_cpu_r_bits_data;
   logic [1:0]            io_cpu_r_bits_resp;
   logic                  io_cpu_r_bits_last;

   logic                  io_cpu_w_valid;
   logic                  io_cpu_w_ready;
   logic [C_DATABITS-1:0] io_cpu_w_bits_data;

   logic                  io_cpu_b_valid;
   logic                  io_cpu_b_ready;
   logic [C_IDBITS-1:0]   io_cpu_b_bits_id;
   logic [1:0]            io_cpu_b_bits_resp;

   logic                  io_r_sink_valid;
   logic                  io_r_sink_ready;
   logic [511:0]          io_r_sink_bits;
   logic                  io_r_source_valid;
   logic                  io_r_source_ready;
   logic [M_DATABITS-1:0] io_r_source_bits;
   logic                  io_r_last;
   logic                  io_w_sink_valid;
   logic                  io_w_sink_ready;
   logic [511:0]          io_w_sink_bits;
   logic                  io_w_source_valid;
   logic                  io_w_source_ready;
   logic [M_DATABITS-1:0] io_w_source_bits;
   logic                  io_w_last;

   /*
    * MEM Wires
    */
   logic                  io_mem_ar_valid;
   logic                  io_mem_ar_ready;
   logic [M_IDBITS-1:0]   io_mem_ar_bits_id;
   logic [25:0]           io_mem_ar_bits_addr;
   logic [7:0]            io_mem_ar_bits_len;
   logic [2:0]            io_mem_ar_bits_size;

   logic                  io_mem_aw_valid;
   logic                  io_mem_aw_ready;
   logic [M_IDBITS-1:0]   io_mem_aw_bits_id;
   logic [25:0]           io_mem_aw_bits_addr;
   logic [7:0]            io_mem_aw_bits_len;
   logic [2:0]            io_mem_aw_bits_size;

   logic                  io_mem_r_valid;
   logic                  io_mem_r_ready;
   logic [M_IDBITS-1:0]   io_mem_r_bits_id;
   logic [511:0]          io_mem_r_bits_data;
   logic [1:0]            io_mem_r_bits_resp;
   logic                  io_mem_r_bits_last;

   logic                  io_mem_w_valid;
   logic                  io_mem_w_ready;
   logic [511:0]          io_mem_w_bits_data;

   logic                  io_mem_b_valid;
   logic                  io_mem_b_ready;
   logic [M_IDBITS-1:0]   io_mem_b_bits_id;

   /*
    * DUT
    */
   MPE mpe ( .* );

   AXI4BusSlave
   #( .IDBITS(M_IDBITS), .DATABITS(M_DATABITS) )
   mem (
     .clock(clock),
     .reset(reset),
     .arready(io_mem_ar_ready),
     .arvalid(io_mem_ar_valid),
     .arid(io_mem_ar_bits_id),
     .araddr({io_mem_ar_bits_addr, 6'h0}),
     .arlen(M_AXLEN[7:0]),
     .arsize(M_AXSIZE[2:0]),
     .awready(io_mem_aw_ready),
     .awvalid(io_mem_aw_valid),
     .awid(io_mem_aw_bits_id),
     .awaddr({io_mem_aw_bits_addr, 6'h0}),
     .awlen(M_AXLEN[7:0]),
     .awsize(M_AXSIZE[2:0]),
     .rready(io_mem_r_ready),
     .rvalid(io_mem_r_valid),
     .rid(io_mem_r_bits_id),
     .rdata(io_mem_r_bits_data),
     .rresp(io_mem_r_bits_resp),
     //.rlast(io_mem_r_bits_last),
     .wready(io_mem_w_ready),
     .wvalid(io_mem_w_valid),
     .wdata(io_mem_w_bits_data),
     .wstrb({M_DATABITS/8{1'b1}}),
     .wlast(1'b1),
     .bready(io_mem_b_ready),
     .bvalid(io_mem_b_valid),
     .bid(io_mem_b_bits_id),
     .bresp(io_mem_b_bits_resp)
   );

   // Helper functions for memory
   logic [511:0] memory[logic[31:0]];

   function [511:0] mem_read(input logic [31:0] addr);
      mem_read = memory.exists(addr) ? memory[addr] : {128{4'hF}};
      $display("  mem_read(%08X)", addr);
      $display("    data: %X", mem_read);
   endfunction // mem_read

   function void mem_write(input logic [31:0] addr, input logic [511:0] data);
      $display("  mem_write(%08X, %X)", addr, data);
      memory[addr] = data;
   endfunction // mem_write


   /*
    * constants
    * */
   localparam KiB = 64'd1024;
   localparam MiB = 64'd1024*1024;
   localparam GiB = 64'd1024*1024*1024;

   // address map in DRAM
   localparam PM_BEGIN   = 64'h40000000;
   localparam PM_END     = PM_BEGIN + 96*MiB;
   localparam META_BEGIN = PM_BEGIN + 96*MiB;
   localparam META_END   = META_BEGIN + 32*MiB;


   /*
    * Clock Generation
    */
   parameter CLK = 2;
   logic [63:0] cycle_counter;
   always #(CLK/2)
   begin
     clock <= !clock;
   end

   always @( posedge clock )
   begin
      $display("current_clock: %0d", cycle_counter);
      cycle_counter <= cycle_counter + 'd1;
   end



   /*
    * Test cases
    */
   logic READ;
   logic WRITE;
   assign READ  = 1'b1;
   assign WRITE = 1'b0;

   logic [543:0] test_case[4095:0];
   logic [31:0]  test_case_size;
   logic [31:0]  test_case_idx;
   logic         test_wo;

   function [511:0] random512(int dummy);
      automatic int unsigned r0 = $urandom();
      automatic int unsigned r1 = $urandom();
      automatic int unsigned r2 = $urandom();
      automatic int unsigned r3 = $urandom();
      automatic int unsigned r4 = $urandom();
      automatic int unsigned r5 = $urandom();
      automatic int unsigned r6 = $urandom();
      automatic int unsigned r7 = $urandom();
      automatic int unsigned r8 = $urandom();
      automatic int unsigned r9 = $urandom();
      automatic int unsigned rA = $urandom();
      automatic int unsigned rB = $urandom();
      automatic int unsigned rC = $urandom();
      automatic int unsigned rD = $urandom();
      automatic int unsigned rE = $urandom();
      automatic int unsigned rF = $urandom();

      random512 = {r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, rA, rB, rC, rD, rE, rF};
   endfunction

   function void push(logic rw, logic [3:0] id, logic [25:0] addr, logic [511:0] data);
      begin
         //test_case.push_back({rw, id, addr, wdata});
         test_case[test_case_size] = {rw, id, addr, data};
         test_case_size            = test_case_size + 'd1;
      end
   endfunction // push

   int next_test_id = 0;
   function void push_seqtest(logic [31:0] addr_begin, logic [31:0] addr_end, logic [31:0] stride);
      for (longint addr = addr_begin; addr < addr_end; addr = addr + stride)
      begin
         if ( (META_BEGIN <= addr) & (addr < META_END) )
           continue;
         push(WRITE, next_test_id[3:0], addr[31:6], random512(1));
         push(READ,  next_test_id[3:0], addr[31:6], 'h0);
         next_test_id = next_test_id + 1;
      end
   endfunction // push_seqtest

   function void push_wotest(logic [31:0] addr_begin, logic [31:0] addr_end, logic [31:0] stride);
      for (longint addr = addr_begin; addr < addr_end; addr = addr + stride)
      begin
         if ( (META_BEGIN <= addr) & (addr < META_END) )
           continue;
         push(WRITE, next_test_id[3:0], addr[31:6], random512(1));
         next_test_id = next_test_id + 1;
         test_wo      = 1'b1;
      end
   endfunction // push_seqtest

   function void push_rotest(logic [31:0] addr_begin, logic [31:0] addr_end, logic [31:0] stride);
      for (longint addr = addr_begin; addr < addr_end; addr = addr + stride)
      begin
         if ( (META_BEGIN <= addr) & (addr < META_END) )
           continue;
         push(READ, next_test_id[3:0], addr[31:6], random512(1));
         next_test_id = next_test_id + 1;
      end
   endfunction // push_seqtest



   // buffer for next test case
   logic         next_rw;
   logic [3:0]   next_id;
   logic [25:0]  next_addr;
   logic [511:0] next_data;
   assign next_rw   = test_case[test_case_idx][542];
   assign next_id   = (test_case_idx == test_case_size) ? 'h0 : test_case[test_case_idx][541:538];
   assign next_addr = (test_case_idx == test_case_size) ? 'h0 : test_case[test_case_idx][537:512];
   assign next_data = (test_case_idx == test_case_size) ? 'h0 : test_case[test_case_idx][511:0];


   /*
    * Initialize metadata
    */
   function void init_mem();
      integer fd;
      logic [31:0] addr;
      logic [55:0] tag;
      logic [511:0] data;

      fd = $fopen("mem_init.txt", "r");
      if (fd == 0)
      begin
         $display("Failed to Open!");
         $finish;
      end
      while (!$feof(fd))
      begin
         $fscanf(fd, "%h %h", addr, data);
         mem_write(addr, data);
      end
      $fclose(fd);
   endfunction // init_mem



   /*
    * AXI4 state machine
    */
   enum {  R_WAIT, R_VERIFY } r_state;
   enum { AW_IDLE, W_BUSY }   aw_state;
   logic [15:0] id_available;

   // read/write data buffers
   logic [2:0]   rdata_idx;
   logic [511:0] rdata;
   logic [2:0]   wdata_idx;
   logic [511:0] wdata;
   logic [1:0]   rresp;
   logic [3:0]   rid;

   // hold expected (written) data to verify
   logic [511:0] expected [3:0];

   // the number of completed test cases
   logic [31:0]  completed_r;
   logic [31:0]  completed_w;

   function void validate_resp(logic [1:0] resp, string header);
      if ( resp == 2'b00 )
      begin
         $display("  %s Validated", header);
      end
      else
      begin
         $display("*** %s ERROR (expected: %d, given: %d)", header, 2'h00, resp);
         $finish;
      end
   endfunction // validate_resp

   function void validate_data(logic [3:0] id, logic [511:0] data, string header);
      // if expected has not id (no valid data has written) skip validation
      if (expected[id] !== 'hx)
      begin
         if ( data == expected[id] )
         begin
            $display("  %s Validated", header);
         end
         else
         begin
            $display("*** %s ERROR", header);
            $display("  expected: %X", expected[id]);
            $display("     given: %X", data);
            $finish;
         end // else: !if( data == expected[id] )
      end // if (expected.exists(id))
   endfunction // validate_data




   /*
    * Main Loop
    */
   logic [31:0] prev_pop_cc;
   always @( test_case_idx )
   begin
      prev_pop_cc <= cycle_counter;
   end


   initial
   begin
      // enable waveform dump
      $dumpfile("tb_MPE.vcd");
      $dumpvars(0, tb_MPE);

      // initialize
      clock          = 1'b0;
      cycle_counter  = 'd0;
      test_case_idx  = 'd0;
      completed_r    = 'd0;
      completed_w    = 'd0;
      prev_pop_cc    = 'd0;
      id_available   = 16'hFFFF;
      test_case_size = 'd0;
      test_wo        = 1'b0;
      init_mem();

      // generate test cases
      push(WRITE, 'd0, 32'h47FFF000 >> 6, 'd0); // initialize root
      push(READ, 'd0, 32'h47FFF000 >> 6, 'd0); // initialize root (to release id)
      //push_seqtest(PM_BEGIN, PM_BEGIN + 4*KiB, 64);
      //push_seqtest(PM_BEGIN, PM_BEGIN + 4*KiB, 64);
      push_seqtest(PM_BEGIN, PM_BEGIN + 16*MiB, 256*KiB);
      push_seqtest(PM_BEGIN, PM_BEGIN + 16*MiB, 256*KiB);

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

      // max simulation clock and idle detection
      while (cycle_counter < 3000000)
      begin
         @( posedge clock );

         if ( (cycle_counter - prev_pop_cc) > 5000 )
         begin
            $display("*** Idle clocks exceeded 5000!!! ");
            $display("***   prev_pop_cc = %d", prev_pop_cc);
            $finish;
         end

         if ( (completed_r + completed_w) == test_case_size )
         begin
            $display("All tests passed");
            $finish;
         end
      end // while (cycle_counter < 30000)
      $display("  Max Simulation Time passed");
      $finish;
   end // initial begin



   /*
    * Tasks
    */

   // tick AR channel
   assign io_cpu_ar_valid = (next_rw == READ);
   assign io_cpu_ar_bits_addr = next_addr;
   assign io_cpu_ar_bits_id   = next_id;
   task tickAR();
      begin
         forever
         begin
            @( posedge clock );
            if (io_cpu_ar_valid & io_cpu_ar_ready)
            begin
               id_available[next_id] <= 1'b0;
               test_case_idx         <= test_case_idx + 'd1;

               $strobe("  AXI4BusMaster::AR::Issue");
               $strobe("    araddr: %08X", io_cpu_ar_bits_addr);
               $strobe("      arid: %d",   io_cpu_ar_bits_id);
            end
         end // forever begin
      end
   endtask // tickAR


   // tick R channel
   assign io_cpu_r_ready = (r_state == R_WAIT);
   task tickR();
      begin
         forever
         begin
            @( posedge clock );
            if ( reset )
            begin
               r_state        <= R_WAIT;
               rdata_idx      <= 'd0;
            end
            else
            begin
               if ( r_state == R_WAIT )
               begin
                  if ( io_cpu_r_valid )
                  begin
                     rdata[rdata_idx*64 +: 64] <= io_cpu_r_bits_data;
                     rdata_idx                 <= rdata_idx + 'd1;
                     rid                       <= io_cpu_r_bits_id;
                     rresp                     <= io_cpu_r_bits_resp;
                     r_state                   <= io_cpu_r_bits_last ? R_VERIFY : R_WAIT;
                  end
               end // if ( r_state == R_WAIT )
               else if ( r_state == R_VERIFY )
               begin
                  rdata_idx <= 'd0;
                  r_state   <= R_WAIT;

                  // assertion
                  validate_resp(rresp, "AXI4BusMaster::R::Resp");
                  validate_data(rid, rdata, "AXI4BusMaster::R::Data");

                  // release id
                  id_available[rid] <= 1'b1;

                  // increase completed test case
                  completed_r <= completed_r + 'd1;

               end // if ( r_state == R_VERIFY )
            end // else: !if( reset )
         end // forever begin
      end
   endtask // tickR


   // tick AW channel
   assign io_cpu_aw_valid     = (aw_state == AW_IDLE) & (next_rw == WRITE) & id_available[next_id];
   assign io_cpu_aw_bits_addr = next_addr;
   assign io_cpu_aw_bits_id   = next_id;
   assign io_cpu_w_valid      = (aw_state == W_BUSY);
   assign io_cpu_w_bits_data  = wdata[wdata_idx*64 +: 64];
   task tickAW();
      begin
         forever
         begin
            @( posedge clock );
            if ( reset )
            begin
               aw_state  <= AW_IDLE;
            end
            else
            begin
               if ( aw_state == AW_IDLE )
               begin
                  if (io_cpu_aw_valid & io_cpu_aw_ready)
                  begin
                     id_available[next_id] <= 1'b0;
                     wdata                 <= next_data;
                     expected[next_id]     <= next_data;
                     wdata_idx             <= 'd0;
                     aw_state              <= W_BUSY;
                     test_case_idx         <= test_case_idx + 'd1;

                     $strobe("  AXI4BusMaster::AW::Issue");
                     $strobe("    awaddr: %08X", io_cpu_aw_bits_addr);
                     $strobe("      awid: %d",   io_cpu_aw_bits_id);
                  end // if (io_cpu_aw_valid & io_cpu_aw_ready)
               end // if ( aw_state == AW_IDLE )
               else if ( aw_state == W_BUSY )
               begin
                  if ( io_cpu_w_ready )
                  begin
                     // under data beats
                     wdata_idx <= wdata_idx + 'd1;
                     aw_state  <= (wdata_idx == 'd7) ? AW_IDLE : W_BUSY;
                  end
               end
            end // else: !if( reset )
         end // forever begin
      end
   endtask // tickAW


   // tick B channel
   assign io_cpu_b_ready = 1'b1;
   task tickB();
      begin
         forever
         begin
            @( posedge clock );

            if ( io_cpu_b_valid )
            begin
               validate_resp(io_cpu_b_bits_resp, "AXI4BusMaster::B::Resp");
               completed_w <= completed_w + 'd1;
               if ( test_wo )
                 id_available[io_cpu_b_bits_id] <= 1'b1;
            end
         end
      end
   endtask // tickB


endmodule // tb_MPE





























//
