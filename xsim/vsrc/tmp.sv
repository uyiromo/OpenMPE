`timescale 1ns/1ps

module tmp();

   logic clock;
   reg reset;
   logic done;
   logic done2;

/* -----\/----- EXCLUDED -----\/-----
   task automatic tick1(ref logic clk);
      forever
      begin
         @(posedge clock);
         $display("tick1 @%d", $time);
      end
   endtask
 -----/\----- EXCLUDED -----/\----- */

   task tick2();
      begin
         enum { A, B } state;

         forever
         begin
            @(posedge clock);
            if (done)
              $display("tick2* @%d", $time);
            else
              $display("tick2! @%d", $time);
         end
      end
   endtask

/* -----\/----- EXCLUDED -----\/-----
   task automatic tick3(const ref logic clock, ref logic done);
      begin
         #30;
         done = 1'b1;
         done2 <= 1'b1;
      end
   endtask
 -----/\----- EXCLUDED -----/\----- */

   task tick3();
      begin
         #30;
         done = 1'b1;
         done2 <= 1'b1;
      end
   endtask

   initial
   begin
      clock <= 1'b0;
      done  <= 1'b0;
      done2 <= 1'b0;
   end

   always #5
     clock <= !clock;

   initial
     begin
        $dumpfile("hoge.vcd");
        $dumpvars(0, tmp);
        fork
           //tick1(clock);
           tick2();
           //tick3(clock, done);
           tick3();
        join_none
        #50;
        $display("done  = %d\n", done);
        $display("done2 = %d\n", done2);
        $finish;
     end
endmodule
