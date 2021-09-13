// See LICENSE for license details.
`timescale 1ns/1ps

module genInitMem();

   // clock/reset
   logic clock;
   logic reset;

   /*
    * Clock Generation
    */
   parameter CLK = 2;
   always #(CLK/2)
   begin
     clock <= !clock;
   end

   logic         source_valid;
   logic [ 25:0] source_addr;
   logic [ 55:0] source_nonce;
   logic [511:0] source_msg;

   logic [ 55:0] tag;
   logic         tag_valid;
   logic         tag_ready;
   logic [511:0] tag_buf;

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
      integer fd;
      fd = $fopen("mem_init.txt", "w+");
      if (fd == 0)
      begin
         $display("Failed to open mem_init.txt");
         $finish;
      end

      // initialize
      clock <= 1'b1;

      // reset
      reset <= 1'b1;
      #10;
      reset <= 1'b0;

      // set input
      //source_addr  <= 26'h11ffe00;
      source_nonce <= 56'h00000000000001;
      source_msg   <= 512'h00000000000000000000000000000100000000000001000000000000010000000000000100000000000001000000000000010000000000000100000000000001;

      // PD_Tag (0x46000000 - 0x46C00000)
      /*for (longint addr = 26'h1180000; addr < 26'h11b0000; addr = addr + 1)
      begin
         tag_buf <= 512'h0;
         for (longint idx = 0; idx < 8; idx = idx + 1)
         begin
            source_addr <= (26'h1000000 + (addr - 26'h1180000) * 8 + idx);
            source_valid <= 1'b0;
            #(CLK);
            source_valid <= 1'b1;
            #(CLK);
            source_valid <= 1'b0;
            while(!tag_valid)
            begin
               @( posedge clock );
               if ( tag_valid )
               begin
                  tag_buf   <= tag_buf | (tag << (idx * 56));
                  tag_ready <= 1'b1;
                  #(CLK);
                  tag_ready <= 1'b0;
                  #(CLK);
                  break;
               end
            end
         end // for (longint idx = 0; idx < 8; idx = idx + 1)
         $fdisplay(fd, "%8x %128x", addr[31:0] << 6, tag_buf);
         $display("%8x %128x",    addr[31:0] << 6, tag_buf);
      end*/

      // CR, L0, L1, L2
      for (longint addr = 26'h11b0000; addr < 26'h1200000; addr = addr + 1)
      begin
         source_addr <= addr[25:0];
         source_valid <= 1'b0;
         #(CLK);
         source_valid <= 1'b1;
         #(CLK);
         source_valid <= 1'b0;
         while(!tag_valid)
         begin
            @( posedge clock );
            if ( tag_valid )
            begin
               $fdisplay(fd, "%8x %128x", addr[31:0] << 6, source_msg | (tag << 448));
               $display("%8x %128x",    addr[31:0] << 6, source_msg | (tag << 448));
               tag_ready <= 1'b1;
               #(CLK);
               tag_ready <= 1'b0;
               #(CLK);
               break;
            end
         end
      end

      $display("  All done");
      $fclose(fd);
      $finish;
   end // initial begin


endmodule // tb_CWMACOpt
