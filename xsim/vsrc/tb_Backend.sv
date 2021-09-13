// See LICENSE for license details.
`timescale 1ns/1ps

`include "Connection.sv"

module tb_Backend();

   // parameters
   parameter IDBITS    = 4;
   parameter DATABITS  = 512;
   parameter NAME      = "CPU";
   parameter BEATS     = 512/DATABITS;
   parameter BEATS_M1  = BEATS - 1;
   parameter DATABYTES = DATABITS/8;

   // clock/reset
   logic clock;
   logic reset;

   // wires
   `WIRE_INTERNAL(io_sink_0);
   `WIRE_INTERNAL(io_sink_1);
   `WIRE_INTERNAL(io_sink_2);
   `WIRE_INTERNAL(io_sink_3);
   `WIRE_INTERNAL(io_sink_4);
   `WIRE_INTERNAL(io_sink_5);
   `WIRE_INTERNAL(io_sink_6);
   `WIRE_INTERNAL(io_sink_7);
   `WIRE_INTERNAL(io_sink_8);
   `WIRE_AXI4(4, 512);

   // assigns
   logic         mvalid [8:0];
   logic         mready [8:0];
   logic [  3:0] mid    [8:0];
   logic [ 31:0] maddr  [8:0];
   logic [511:0] mdata  [8:0];
   logic         mrw    [8:0];
   logic         svalid [8:0];
   logic         sready [8:0];
   logic [  3:0] sid    [8:0];
   logic [511:0] sdata  [8:0];
   logic [  1:0] sresp  [8:0];
   logic         srw    [8:0];
   `ASSIGN_INTERNAL(0, io_sink_0);
   `ASSIGN_INTERNAL(1, io_sink_1);
   `ASSIGN_INTERNAL(2, io_sink_2);
   `ASSIGN_INTERNAL(3, io_sink_3);
   `ASSIGN_INTERNAL(4, io_sink_4);
   `ASSIGN_INTERNAL(5, io_sink_5);
   `ASSIGN_INTERNAL(6, io_sink_6);
   `ASSIGN_INTERNAL(7, io_sink_7);
   `ASSIGN_INTERNAL(8, io_sink_8);

   // DUT
   `DUT_BACKEND();
   `DUT_MEMORY(IDBITS, DATABITS);

   // states
   enum { M_IDLE, M_BUSY }  m_state;
   enum { S_IDLE, S_BUSY }  s_state;

   // memory
   logic [511:0]        memory[logic[31:0]];

   function [511:0] mem_read(input logic [31:0] addr);
      mem_read = memory.exists(addr) ? memory[addr] : 'hx;
   endfunction // mem_read

   function void mem_write(input logic [31:0] addr, input logic [511:0] data);
      memory[addr] = data;
   endfunction // mem_write



   /*
    * Clock Generation
    */
   parameter MAX_CYCLE_COUNTER = 10000;
   parameter CLK = 2;
   logic [63:0] time_counter;
   logic [63:0] cycle_counter;
   always #(CLK/2)
   begin
     clock <= !clock;
     time_counter <= time_counter + 'd1;
   end

   initial
     forever
     begin
        @( posedge clock );
        $display("current_clock: %0d", cycle_counter);
        cycle_counter <= cycle_counter + 'd1;
     end


   localparam KiB = 64'd1024;
   localparam MiB = 64'd1024*1024;
   localparam GiB = 64'd1024*1024*1024;

   logic [3:0] sink_choice;
   enum  { SINK_MIDLE, SINK_MBUSY, SINK_SIDLE, SINK_SBUSY } sink_state [8:0];
   logic [3:0] expected_order [8:0];
   logic [3:0] completed_order [8:0];
   logic       is_completed_order_expected;
   logic [3:0] completed_order_pos;
   localparam [0:0] DOING_READ  = 1'b1;
   localparam [0:0] DOING_WRITE = 1'b0;
   logic       doing_rw;

   assign is_completed_order_expected
     = (completed_order[0] == expected_order[0])
       & (completed_order[1] == expected_order[1])
         & (completed_order[2] == expected_order[2])
         & (completed_order[3] == expected_order[3])
         & (completed_order[4] == expected_order[4])
         & (completed_order[5] == expected_order[5])
         & (completed_order[6] == expected_order[6])
         & (completed_order[7] == expected_order[7])
         & (completed_order[8] == expected_order[8]);

   /*
    * Loop
    */
   initial
   begin
      // enable waveform dump
      $dumpfile("tb_Backend.vcd");
      $dumpvars(0, tb_Backend);

      // initialize
      clock          <= 1'b0;
      cycle_counter  <= 'd0;
      time_counter   <= 'd0;
      sink_choice    <= 'd0;
      for (int i = 0; i < 9; i = i + 1)
         sink_state[i] <= SINK_MIDLE;
      doing_rw       <= DOING_WRITE;


      // main loop
      fork
         tick('d0);
         tick('d1);
         tick('d2);
         tick('d3);
         tick('d4);
         tick('d5);
         tick('d6);
         tick('d8);
         tick('d7);
      join_none

      // reset
      reset <= 1'b1;
      #20;
      reset <= 1'b0;

      // Issue Write simultaneously
      doing_rw               <= DOING_WRITE;
      completed_order_pos    <= 'd0;
      expected_order         <= {'d0, 'd1, 'd2, 'd3, 'd4, 'd5, 'd6, 'd7, 'd8};
      issue('d0);
      issue('d1);
      issue('d2);
      issue('d3);
      issue('d4);
      issue('d5);
      issue('d6);
      issue('d7);
      issue('d8);
      wait_finish();

      // Issue Read simultaneously
      doing_rw            <= DOING_WRITE;
      completed_order_pos <= 'd0;
      expected_order      <= {'d0, 'd1, 'd2, 'd3, 'd4, 'd5, 'd6, 'd7, 'd8};
      issue('d0);
      issue('d1);
      issue('d2);
      issue('d3);
      issue('d4);
      issue('d5);
      issue('d6);
      issue('d7);
      issue('d8);
      wait_finish();

      // Issue Read with delay
      doing_rw            <= DOING_WRITE;
      completed_order_pos <= 'd0;
      expected_order      <= {'d0, 'd2, 'd4, 'd6, 'd8, 'd1, 'd3, 'd5, 'd7 };
      issue('d0);
      #1;
      issue('d2);
      #1;
      issue('d4);
      #1;
      issue('d6);
      #1;
      issue('d8);
      #1;
      issue('d1);
      #1;
      issue('d3);
      #1;
      issue('d5);
      #1;
      issue('d7);
      #1;
      wait_finish();


      $finish;
   end // initial begin



   /*
    * Tasks
    */
   task wait_finish();
      while (cycle_counter < MAX_CYCLE_COUNTER)
      begin
         @( posedge clock );
         if (completed_order_pos == 'd9)
         begin
            $display("*** All tests passed ***");
            if ( !is_completed_order_expected )
            begin
               $display("*** completed_order is expected ! ***");
               return;
            end
            else
            begin
               $display("*** completed_order VIOLATES expected ! ***");
               $display("*** completed_order: [%d, %d, %d, %d, %d, %d, %d, %d, %d]",
                        completed_order['d0], completed_order['d1], completed_order['d2],
                        completed_order['d3], completed_order['d4], completed_order['d5],
                        completed_order['d6], completed_order['d7], completed_order['d8]);
               $display("*** expected_order: [%d, %d, %d, %d, %d, %d, %d, %d, %d]",
                        expected_order['d0], expected_order['d1], expected_order['d2],
                        expected_order['d3], expected_order['d4], expected_order['d5],
                        expected_order['d6], expected_order['d7], expected_order['d8]);
               $fatal(0);
            end
         end
      end // while (time_counter < 3000000)

      $display("  Max Simulation Time passed");
      $finish;
   endtask



   task automatic issue(input [3:0] choice);
      mvalid[choice]     <= 1'b1;
      mid[choice]        <= choice;
      maddr[choice]      <= choice * MiB;
      mdata[choice]      <= choice * GiB;
      mrw[choice]        <= doing_rw;
      sink_state[choice] <= SINK_MBUSY;

      $display("  SINK_MIDLE (choice: %d)", choice);
      $display("      mid: %d", choice);
      $display("    maddr: %X", choice * MiB);
      $display("    mdata: %X", choice * GiB);
      $display("      mrw: %d", doing_rw);
   endtask // issue


   task automatic tick(input [3:0] choice);
      begin
         forever
         begin
            @( posedge clock );
            if ( reset )
            begin
               mvalid[choice] <= 1'b0;
               sready[choice] <= 1'b0;
            end
            else
            begin
               if ( sink_state[choice] == SINK_MBUSY )
               begin
                  // waiting write handshake
                  if ( mvalid[choice] & mready[choice] )
                  begin
                     mvalid[choice]     <= 'd0;
                     sink_state[choice] <= SINK_SIDLE;
                  end
               end
               else if ( sink_state[choice] == SINK_SIDLE)
               begin
                  // waiting for slave valid
                  if ( svalid[choice] )
                  begin
                     // id assertion
                     if ( sid[choice] != choice )
                     begin
                        $display("  ***ERROR*** sid[%d](%d) != choice(%d)",
                                 choice, sid[choice], choice);
                        $fatal(0);
                     end

                     // data assertion
                     if ( (doing_rw == DOING_READ) & (sdata[choice] != (choice*GiB)) )
                     begin
                        $display("  ***ERROR*** sdata[%d](%X) != expect(%X)",
                                 choice, sdata[choice], choice*GiB);
                        $fatal(0);
                     end

                     sready[choice]     <= 1'b1;
                     sink_state[choice] <= SINK_SBUSY;
                  end // if ( svalid[choice] )
               end // if ( sink_state[choice] == SINK_SIDLE)
               else if ( sink_state[choice] == SINK_SBUSY )
               begin
                  if ( svalid[choice] & sready[choice] )
                  begin
                     sready[choice]     <= 1'b0;
                     sink_state[choice] <= SINK_MIDLE;

                     completed_order[completed_order_pos] <= choice;
                     completed_order_pos <= completed_order_pos + 'd1;
                  end
               end // if ( sink_state[choice] == SINK_SBUSY )
            end // else: !if( reset )
         end // forever begin
      end
   endtask // tick






endmodule // tb_Backend





























//
