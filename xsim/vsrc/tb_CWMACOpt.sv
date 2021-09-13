`timescale 1ns/1ps

module tb_CWMACOpt();

   // clock/reset
   logic clock;
   logic reset;

   /*
    * Clock Generation
    */
   parameter CLK = 2;
   logic [63:0] time_counter;
   logic [63:0] cycle_counter;
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

   logic         source_valid;
   logic [ 25:0] source_addr;
   logic [ 55:0] source_nonce;
   logic [511:0] source_msg;

   logic [ 55:0] tag;
   logic         tag_valid;
   logic         tag_ready;
   logic [ 55:0] expected_tag;

   logic [127:0] key_enc;
   logic [511:0] key_hash;
   assign key_enc  = 128'h000102030405060708090a0b0c0d0e0f;
   assign key_hash = 512'hfedcba9876543210fedcba9876543210ffeeddccbbaa998877665544332211000123456789abcdef0123456789abcdef00112233445566778899aabbccddeeff;

   CWMACOpt CWMACOpt(
     .clock(clock),
     .reset(reset),
     .io_source_bits_addr(source_addr),
     .io_source_bits_nonce(source_nonce),
     .io_source_bits_msg(source_msg),
     .io_source_valid(source_valid),
     .io_tag_bits(tag),
     .io_tag_valid(tag_valid),
     .io_tag_ready(tag_ready),
     .io_keyEnc(key_enc),
     .io_keyHash(key_hash)
   );

   /*
    * Loop
    */
   initial
   begin
      // enable waveform dump
      $dumpfile("tb_CWMACOpt.vcd");
      $dumpvars(0, tb_CWMACOpt);

      // initialize
      clock         <= 1'b1;
      time_counter  <= 'h0;
      cycle_counter <= 'h0;

      // reset
      reset <= 1'b1;
      #10;
      reset <= 1'b0;

      // set input
      source_addr  <= 26'h11ffe00;
      source_nonce <= 56'h00000000000002;
      source_msg   <= 512'h00000000000000000000000000000100000000000001000000000000010000000000000100000000000001000000000000010000000000000100000000000002;
      source_valid <= 1'b0;
      #(CLK);
      source_valid <= 1'b1;
      expected_tag <= 56'h9d906ad9445061;
      #(CLK);
      source_valid <= 1'b0;

      while (time_counter < 500)
      begin
         @( posedge clock );

         if ( tag_valid )
         begin
            if ( tag == expected_tag )
            begin
               $display("*** Tag is expected!");
               $finish;
            end
            else
            begin
               $display("*** Tag IS NOT EXPECTED!!!");
               $display("  Expected: %X", expected_tag);
               $display("  Computed: %X", tag);
               break;
            end
         end
      end

      $display("  All tests passed");
      $finish;
   end // initial begin


endmodule // tb_CWMACOpt
