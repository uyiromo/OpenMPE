`timescale 1ns/1ps

module tb_AES128Opt();

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

   logic         plain_valid;
   logic [ 25:0] addr;
   logic [ 55:0] nonce;
   logic [127:0] key;
   logic [511:0] cipher;
   logic         cipher_valid;
   logic [511:0] expected_cipher;

   assign key = 128'hx000102030405060708090a0b0c0d0e0f;

   AES128Opt AES128Opt(
     .clock(clock),
     .reset(reset),
     .io_plain_bits_addr(addr),
     .io_plain_bits_nonce(nonce),
     .io_plain_valid(plain_valid),
     .io_key(key),
     .io_cipher(cipher),
     .io_cipherValid(cipher_valid)
   );

   /*
    * Loop
    */
   initial
   begin
      // enable waveform dump
      $dumpfile("tb_AES128Opt.vcd");
      $dumpvars(0, tb_AES128Opt);

      // initialize
      clock         <= 1'b1;
      time_counter  <= 'h0;
      cycle_counter <= 'h0;

      // reset
      reset <= 1'b1;
      #10;
      reset <= 1'b0;

      // set input
      addr  <= 26'h1000000;
      nonce <= 56'h00000000000002;
      plain_valid <= 1'b0;
      #2;
      plain_valid <= 1'b1;
      #1;
      plain_valid <= 1'b0;

      expected_cipher <= 512'h739a7b048873b2931acb2efb1452391c8f8c45c9c55decf8fde10ccbf6301d18c2a0a54c3b376e4d71c690e3c647972a906b4108821f8726cc149c276b3f3eac;

      while (time_counter < 500)
      begin
         @( posedge clock );

         if (cipher_valid)
         begin
            if ( cipher == expected_cipher )
            begin
               $display("*** Cipher is expected!");
               $finish;
            end
            else
            begin
               $display("*** Cipher IS NOT EXPECTED!!!");
               $display("  Expected: %X", expected_cipher);
               $display("    Cipher: %X", cipher);
               $finish;
            end
         end
      end

      $display("  All tests passed");
      $finish;
   end // initial begin


endmodule // tb_AES128Opt
