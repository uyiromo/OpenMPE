// See LICENSE for license details.
/*
 * For tb_Frontend
 */
/*
`define TREE(N)                                \
    logic         tree_``N``_master_ready;     \
    logic         tree_``N``_master_valid;     \
    logic [  3:0] tree_``N``_master_bits_id;   \
    logic [ 25:0] tree_``N``_master_bits_addr; \
    logic [511:0] tree_``N``_master_bits_data; \
    logic         tree_``N``_master_bits_rw;   \
    logic         tree_``N``_slave_ready;      \
    logic         tree_``N``_slave_valid;      \
    logic [  3:0] tree_``N``_slave_bits_id;    \
    logic [511:0] tree_``N``_slave_bits_data;  \
    logic [  1:0] tree_``N``_slave_bits_resp;  \
    logic         tree_``N``_slave_bits_rw;    \
                                               \
    InternalBusSlave #(                        \
        .NAME("Tree#N")                        \
    ) tree``N``(                               \
        .clock(clock),                         \
        .reset(reset),                         \
        .mvalid(tree_``N``_master_valid),      \
        .mready(tree_``N``_master_ready),      \
        .mid(tree_``N``_master_bits_id),       \
        .maddr(tree_``N``_master_bits_addr),   \
        .mdata(tree_``N``_master_bits_data),   \
        .mrw(tree_``N``_master_bits_rw),       \
        .svalid(tree_``N``_slave_valid),       \
        .sready(tree_``N``_slave_ready),       \
        .sid(tree_``N``_slave_bits_id),        \
        .sdata(tree_``N``_slave_bits_data),    \
        .sresp(tree_``N``_slave_bits_resp),    \
        .srw(tree_``N``_slave_bits_rw)         \
    );
*/
/*
`define BACKEND(N)                          \
    logic         backend_master_ready;     \
    logic         backend_master_valid;     \
    logic [  3:0] backend_master_bits_id;   \
    logic [ 25:0] backend_master_bits_addr; \
    logic [511:0] backend_master_bits_data; \
    logic         backend_master_bits_rw;   \
    logic         backend_slave_ready;      \
    logic         backend_slave_valid;      \
    logic [  3:0] backend_slave_bits_id;    \
    logic [511:0] backend_slave_bits_data;  \
    logic [  1:0] backend_slave_bits_resp;  \
    logic         backend_slave_bits_rw;    \
                                            \
    InternalBusSlave #(                     \
        .NAME("Backend")                    \
    ) backend(                              \
        .clock(clock),                      \
        .reset(reset),                      \
        .mvalid(backend_master_valid),      \
        .mready(backend_master_ready),      \
        .mid(backend_master_bits_id),       \
        .maddr(backend_master_bits_addr),   \
        .mdata(backend_master_bits_data),   \
        .mrw(backend_master_bits_rw),       \
        .svalid(backend_slave_valid),       \
        .sready(backend_slave_ready),       \
        .sid(backend_slave_bits_id),        \
        .sdata(backend_slave_bits_data),    \
        .sresp(backend_slave_bits_resp),    \
        .srw(backend_slave_bits_rw)         \
    );
*/
/*
`define FE_TREE_CONNECTION(N)                                     \
    .io_tree_``N``_master_ready(tree_``N``_master_ready),         \
    .io_tree_``N``_master_valid(tree_``N``_master_valid),         \
    .io_tree_``N``_master_bits_id(tree_``N``_master_bits_id),     \
    .io_tree_``N``_master_bits_addr(tree_``N``_master_bits_addr), \
    .io_tree_``N``_master_bits_data(tree_``N``_master_bits_data), \
    .io_tree_``N``_master_bits_rw(tree_``N``_master_bits_rw),     \
    .io_tree_``N``_slave_ready(tree_``N``_slave_ready),           \
    .io_tree_``N``_slave_valid(tree_``N``_slave_valid),           \
    .io_tree_``N``_slave_bits_id(tree_``N``_slave_bits_id),       \
    .io_tree_``N``_slave_bits_data(tree_``N``_slave_bits_data),   \
    .io_tree_``N``_slave_bits_resp(tree_``N``_slave_bits_resp),   \
    .io_tree_``N``_slave_bits_rw(tree_``N``_slave_bits_rw),
*/
/*
`define FE_BACKEND_CONNECTION(N)                            \
    .io_backend_master_ready(backend_master_ready),         \
    .io_backend_master_valid(backend_master_valid),         \
    .io_backend_master_bits_id(backend_master_bits_id),     \
    .io_backend_master_bits_addr(backend_master_bits_addr), \
    .io_backend_master_bits_data(backend_master_bits_data), \
    .io_backend_master_bits_rw(backend_master_bits_rw),     \
    .io_backend_slave_ready(backend_slave_ready),           \
    .io_backend_slave_valid(backend_slave_valid),           \
    .io_backend_slave_bits_id(backend_slave_bits_id),       \
    .io_backend_slave_bits_data(backend_slave_bits_data),   \
    .io_backend_slave_bits_resp(backend_slave_bits_resp),   \
    .io_backend_slave_bits_rw(backend_slave_bits_rw),
*/
/*
`define FRONTEND(N)                    \
    Frontend fe(                       \
      .clock(clock),                   \
      .reset(reset),                   \
      `FE_TREE_CONNECTION(0)           \
      `FE_TREE_CONNECTION(1)           \
      `FE_TREE_CONNECTION(2)           \
      `FE_TREE_CONNECTION(3)           \
      `FE_TREE_CONNECTION(4)           \
      `FE_TREE_CONNECTION(5)           \
      `FE_TREE_CONNECTION(6)           \
      `FE_TREE_CONNECTION(7)           \
      `FE_BACKEND_CONNECTION(0)        \
      .io_cpu_ar_ready(arready),       \
      .io_cpu_ar_valid(arvalid),       \
      .io_cpu_ar_bits_id(arid),        \
      .io_cpu_ar_bits_addr(araddr),    \
      .io_cpu_ar_bits_len(arlen),      \
      .io_cpu_ar_bits_size(arsize),    \
      .io_cpu_ar_bits_burst(arburst),  \
      .io_cpu_ar_bits_lock(arlock),    \
      .io_cpu_ar_bits_cache(arcache),  \
      .io_cpu_ar_bits_prot(arprot),    \
      .io_cpu_ar_bits_qos(arqos),      \
      .io_cpu_aw_ready(awready),       \
      .io_cpu_aw_valid(awvalid),       \
      .io_cpu_aw_bits_id(awid),        \
      .io_cpu_aw_bits_addr(awaddr),    \
      .io_cpu_aw_bits_len(awlen),      \
      .io_cpu_aw_bits_size(awsize),    \
      .io_cpu_aw_bits_burst(awburst),  \
      .io_cpu_aw_bits_lock(awlock),    \
      .io_cpu_aw_bits_cache(awcache),  \
      .io_cpu_aw_bits_prot(awprot),    \
      .io_cpu_aw_bits_qos(awqos),      \
      .io_cpu_r_valid(rvalid),         \
      .io_cpu_r_ready(rready),         \
      .io_cpu_r_bits_id(rid),          \
      .io_cpu_r_bits_data(rdata),      \
      .io_cpu_r_bits_resp(rresp),      \
      .io_cpu_r_bits_last(rlast),      \
      .io_cpu_w_valid(wvalid),         \
      .io_cpu_w_ready(wready),         \
      .io_cpu_w_bits_data(wdata),      \
      .io_cpu_w_bits_strb(wstrb),      \
      .io_cpu_w_bits_last(wlast),      \
      .io_cpu_b_valid(bvalid),         \
      .io_cpu_b_ready(bready),         \
      .io_cpu_b_bits_id(bid),          \
      .io_cpu_b_bits_resp(bresp)       \
    );
*/






/*
 * For tb_Backend
 */
/*
`define WIRE_INTERNAL(PREFIX)                 \
   logic         ``PREFIX``_master_valid;     \
   logic         ``PREFIX``_master_ready;     \
   logic [  3:0] ``PREFIX``_master_bits_id;   \
   logic [ 25:0] ``PREFIX``_master_bits_addr; \
   logic [511:0] ``PREFIX``_master_bits_data; \
   logic         ``PREFIX``_master_bits_rw;   \
   logic         ``PREFIX``_slave_valid;      \
   logic         ``PREFIX``_slave_ready;      \
   logic [  3:0] ``PREFIX``_slave_bits_id;    \
   logic [511:0] ``PREFIX``_slave_bits_data;  \
   logic [  1:0] ``PREFIX``_slave_bits_resp;  \
   logic         ``PREFIX``_slave_bits_rw;
*/
/*
`define ASSIGN_INTERNAL(INDEX, PREFIX)                              \
   assign ``PREFIX``_master_valid     = mvalid[``INDEX``];          \
   assign  mready[``INDEX``]          = ``PREFIX``_master_ready;    \
   assign ``PREFIX``_master_bits_id   = mid[``INDEX``];             \
   assign ``PREFIX``_master_bits_addr = maddr[``INDEX``];           \
   assign ``PREFIX``_master_bits_data = mdata[``INDEX``];           \
   assign ``PREFIX``_master_bits_rw   = mrw[``INDEX``];             \
   assign svalid[``INDEX``]           = ``PREFIX``_slave_valid;     \
   assign ``PREFIX``_slave_ready      = sready[``INDEX``];          \
   assign sid[``INDEX``]              = ``PREFIX``_slave_bits_id;   \
   assign sdata[``INDEX``]            = ``PREFIX``_slave_bits_data; \
   assign sresp[``INDEX``]            = ``PREFIX``_slave_bits_resp; \
   assign srw[``INDEX``]              = ``PREFIX``_slave_bits_rw;
*/
/*
`define WIRE_AXI4(IDBITS, DATABITS) \
   logic                arvalid;    \
   logic                arready;    \
   logic [IDBITS-1:0]   arid;       \
   logic [25:0]         araddr;     \
   logic [7:0]          arlen;      \
   logic [2:0]          arsize;     \
   logic [1:0]          arburst;    \
   logic                arlock;     \
   logic [3:0]          arcache;    \
   logic [2:0]          arprot;     \
   logic [3:0]          arqos;      \
                                    \
   logic                awvalid;    \
   logic                awready;    \
   logic [IDBITS-1:0]   awid;       \
   logic [25:0]         awaddr;     \
   logic [7:0]          awlen;      \
   logic [2:0]          awsize;     \
   logic [1:0]          awburst;    \
   logic                awlock;     \
   logic [3:0]          awcache;    \
   logic [2:0]          awprot;     \
   logic [3:0]          awqos;      \
                                    \
   logic                rvalid;     \
   logic                rready;     \
   logic [IDBITS-1:0]   rid;        \
   logic [DATABITS-1:0] rdata;      \
   logic [1:0]          rresp;      \
   logic                rlast;      \
                                    \
   logic                  wvalid;   \
   logic                  wready;   \
   logic [DATABITS-1:0]   wdata;    \
   logic [DATABITS/8-1:0] wstrb;    \
   logic                  wlast;    \
                                    \
   logic                  bvalid;   \
   logic                  bready;   \
   logic [IDBITS-1:0]     bid;      \
   logic [1:0]            bresp;
*/
/*
`define DUT_BACKEND_SINK(N)                                          \
    .io_sink_``N``_master_ready(io_sink_``N``_master_ready),         \
    .io_sink_``N``_master_valid(io_sink_``N``_master_valid),         \
    .io_sink_``N``_master_bits_id(io_sink_``N``_master_bits_id),     \
    .io_sink_``N``_master_bits_addr(io_sink_``N``_master_bits_addr), \
    .io_sink_``N``_master_bits_data(io_sink_``N``_master_bits_data), \
    .io_sink_``N``_master_bits_rw(io_sink_``N``_master_bits_rw),     \
    .io_sink_``N``_slave_ready(io_sink_``N``_slave_ready),           \
    .io_sink_``N``_slave_valid(io_sink_``N``_slave_valid),           \
    .io_sink_``N``_slave_bits_id(io_sink_``N``_slave_bits_id),       \
    .io_sink_``N``_slave_bits_data(io_sink_``N``_slave_bits_data),   \
    .io_sink_``N``_slave_bits_resp(io_sink_``N``_slave_bits_resp),   \
    .io_sink_``N``_slave_bits_rw(io_sink_``N``_slave_bits_rw),
*/

/*
`define DUT_BACKEND(DUMMY)  \
   Backend be (             \
     .clock(clock),         \
     .reset(reset),         \
     `DUT_BACKEND_SINK(0)   \
     `DUT_BACKEND_SINK(1)   \
     `DUT_BACKEND_SINK(2)   \
     `DUT_BACKEND_SINK(3)   \
     `DUT_BACKEND_SINK(4)   \
     `DUT_BACKEND_SINK(5)   \
     `DUT_BACKEND_SINK(6)   \
     `DUT_BACKEND_SINK(7)   \
     `DUT_BACKEND_SINK(8)   \
     .io_mem_ar_ready(arready),      \
     .io_mem_ar_valid(arvalid),      \
     .io_mem_ar_bits_id(arid),       \
     .io_mem_ar_bits_addr(araddr),   \
     .io_mem_ar_bits_len(arlen),     \
     .io_mem_ar_bits_size(arsize),   \
     .io_mem_ar_bits_burst(arburst), \
     .io_mem_ar_bits_lock(arlock),   \
     .io_mem_ar_bits_cache(arcache), \
     .io_mem_ar_bits_prot(arprot),   \
     .io_mem_ar_bits_qos(arqos),     \
     .io_mem_aw_ready(awready),      \
     .io_mem_aw_valid(awvalid),      \
     .io_mem_aw_bits_id(awid),       \
     .io_mem_aw_bits_addr(awaddr),   \
     .io_mem_aw_bits_len(awlen),     \
     .io_mem_aw_bits_size(awsize),   \
     .io_mem_aw_bits_burst(awburst), \
     .io_mem_aw_bits_lock(awlock),   \
     .io_mem_aw_bits_cache(awcache), \
     .io_mem_aw_bits_prot(awprot),   \
     .io_mem_aw_bits_qos(awqos),     \
     .io_mem_r_ready(rready),        \
     .io_mem_r_valid(rvalid),        \
     .io_mem_r_bits_id(rid),         \
     .io_mem_r_bits_data(rdata),     \
     .io_mem_r_bits_resp(rresp),     \
     .io_mem_r_bits_last(rlast),     \
     .io_mem_w_ready(wready),        \
     .io_mem_w_valid(wvalid),        \
     .io_mem_w_bits_data(wdata),     \
     .io_mem_w_bits_strb(wstrb),     \
     .io_mem_w_bits_last(wlast),     \
     .io_mem_b_ready(bready),        \
     .io_mem_b_valid(bvalid),        \
     .io_mem_b_bits_id(bid),         \
     .io_mem_b_bits_resp(bresp)      \
   );
*/
/*
`define DUT_MEMORY(IDBITS, DATABITS)  \
   AXI4BusSlave                             \
   #( .IDBITS(IDBITS), .DATABITS(DATABITS) ) \
   mem (                    \
     .clock(clock),     \
     .reset(reset),     \
     .arready(arready), \
     .arvalid(arvalid), \
     .arid(arid),       \
     .araddr(araddr),   \
     .arlen(arlen),     \
     .arsize(arsize),   \
     .arburst(arburst), \
     .arlock(arlock),   \
     .arcache(arcache), \
     .arprot(arprot),   \
     .arqos(arqos),     \
     .awready(awready), \
     .awvalid(awvalid), \
     .awid(awid),       \
     .awaddr(awaddr),   \
     .awlen(awlen),     \
     .awsize(awsize),   \
     .awburst(awburst), \
     .awlock(awlock),   \
     .awcache(awcache), \
     .awprot(awprot),   \
     .awqos(awqos),     \
     .rready(rready),   \
     .rvalid(rvalid),   \
     .rid(rid),         \
     .rdata(rdata),     \
     .rresp(rresp),     \
     .rlast(rlast),     \
     .wready(wready),   \
     .wvalid(wvalid),   \
     .wdata(wdata),     \
     .wstrb(wstrb),     \
     .wlast(wlast),     \
     .bready(bready),   \
     .bvalid(bvalid),   \
     .bid(bid),         \
     .bresp(bresp)      \
   );

*/



/*
 * for MPE
 */
`define WIRE_CPU(IDBITS, DATABITS)               \
   /* START: WIRE_CPU_ACXI4 */                   \
   logic                io_cpu_ar_valid;         \
   logic                io_cpu_ar_ready;         \
   logic [IDBITS-1:0]   io_cpu_ar_bits_id;       \
   logic [25:0]         io_cpu_ar_bits_addr;     \
                                                 \
   logic                io_cpu_aw_valid;         \
   logic                io_cpu_aw_ready;         \
   logic [IDBITS-1:0]   io_cpu_aw_bits_id;       \
   logic [25:0]         io_cpu_aw_bits_addr;     \
                                                 \
   logic                io_cpu_r_valid;          \
   logic                io_cpu_r_ready;          \
   logic [IDBITS-1:0]   io_cpu_r_bits_id;        \
   logic [DATABITS-1:0] io_cpu_r_bits_data;      \
   logic [1:0]          io_cpu_r_bits_resp;      \
   logic                io_cpu_r_bits_last;      \
                                                 \
   logic                  io_cpu_w_valid;        \
   logic                  io_cpu_w_ready;        \
   logic [DATABITS-1:0]   io_cpu_w_bits_data;    \
                                                 \
   logic                  io_cpu_b_valid;        \
   logic                  io_cpu_b_ready;        \
   logic [IDBITS-1:0]     io_cpu_b_bits_id;      \
   logic [1:0]            io_cpu_b_bits_resp;    \
   /*  END : WIRE_CPU_AXI4 */



`define WIRE_MEM(IDBITS, DATABITS)               \
   /* START: WIRE_MEM_AXI4 */                    \
   logic                io_mem_ar_valid;         \
   logic                io_mem_ar_ready;         \
   logic [IDBITS-1:0]   io_mem_ar_bits_id;       \
   logic [25:0]         io_mem_ar_bits_addr;     \
   logic [7:0]          io_mem_ar_bits_len;      \
   logic [2:0]          io_mem_ar_bits_size;     \
                                                 \
   logic                io_mem_aw_valid;         \
   logic                io_mem_aw_ready;         \
   logic [IDBITS-1:0]   io_mem_aw_bits_id;       \
   logic [25:0]         io_mem_aw_bits_addr;     \
   logic [7:0]          io_mem_aw_bits_len;      \
   logic [2:0]          io_mem_aw_bits_size;     \
                                                 \
   logic                io_mem_r_valid;          \
   logic                io_mem_r_ready;          \
   logic [IDBITS-1:0]   io_mem_r_bits_id;        \
   logic [DATABITS-1:0] io_mem_r_bits_data;      \
   logic [1:0]          io_mem_r_bits_resp;      \
   logic                io_mem_r_bits_last;      \
                                                 \
   logic                  io_mem_w_valid;        \
   logic                  io_mem_w_ready;        \
   logic [DATABITS-1:0]   io_mem_w_bits_data;    \
   logic [DATABITS/8-1:0] io_mem_w_bits_strb;    \
                                                 \
   logic                  io_mem_b_valid;        \
   logic                  io_mem_b_ready;        \
   logic [IDBITS-1:0]     io_mem_b_bits_id;      \
   logic [1:0]            io_mem_b_bits_resp;    \
   /*  END : WIRE_MEM_AXI4 */


`define DUT_MPE(DUMMY)     \
   MPE mpe (               \
     .*                    \
   );


`define DUT_MEM(_IDBITS, _DATABITS)  \
   AXI4BusSlave                               \
   #( .IDBITS(_IDBITS), .DATABITS(_DATABITS) ) \
   mem (                             \
     .clock(clock),                  \
     .reset(reset),                  \
     .arready(io_mem_ar_ready),      \
     .arvalid(io_mem_ar_valid),      \
     .arid(io_mem_ar_bits_id),       \
     .araddr({io_mem_ar_bits_addr, 6'h0}), \
     .arlen(8'd0),                   \
     .arsize(3'd6),                  \
     .awready(io_mem_aw_ready),      \
     .awvalid(io_mem_aw_valid),      \
     .awid(io_mem_aw_bits_id),       \
     .awaddr({io_mem_aw_bits_addr, 6'h0}), \
     .awlen(8'd0),                   \
     .awsize(3'd6),                  \
     .rready(io_mem_r_ready),        \
     .rvalid(io_mem_r_valid),        \
     .rid(io_mem_r_bits_id),         \
     .rdata(io_mem_r_bits_data),     \
     .rresp(io_mem_r_bits_resp),     \
     .rlast(io_mem_r_bits_last),     \
     .wready(io_mem_w_ready),        \
     .wvalid(io_mem_w_valid),        \
     .wdata(io_mem_w_bits_data),     \
     .wstrb(64'hFFFFFFFFFFFFFFFF),   \
     .wlast(1'b1),                   \
     .bready(io_mem_b_ready),        \
     .bvalid(io_mem_b_valid),        \
     .bid(io_mem_b_bits_id),         \
     .bresp(io_mem_b_bits_resp)      \
   );


`define AXI4_ALIAS(IDBITS, DATABITS, PREFIX) \
   logic                arvalid;             \
   logic                arready;             \
   logic [IDBITS-1:0]   arid;                \
   logic [25:0]         araddr;              \
   logic [7:0]          arlen;               \
   logic [2:0]          arsize;              \
   logic [1:0]          arburst;             \
   logic                arlock;              \
   logic [3:0]          arcache;             \
   logic [2:0]          arprot;              \
   logic [3:0]          arqos;               \
                                             \
   logic                awvalid;             \
   logic                awready;             \
   logic [IDBITS-1:0]   awid;                \
   logic [25:0]         awaddr;              \
   logic [7:0]          awlen;               \
   logic [2:0]          awsize;              \
   logic [1:0]          awburst;             \
   logic                awlock;              \
   logic [3:0]          awcache;             \
   logic [2:0]          awprot;              \
   logic [3:0]          awqos;               \
                                             \
   logic                rvalid;              \
   logic                rready;              \
   logic [IDBITS-1:0]   rid;                 \
   logic [DATABITS-1:0] rdata;               \
   logic [1:0]          rresp;               \
   logic                rlast;               \
                                             \
   logic                  wvalid;            \
   logic                  wready;            \
   logic [DATABITS-1:0]   wdata;             \
   logic [DATABITS/8-1:0] wstrb;             \
   logic                  wlast;             \
                                             \
   logic                  bvalid;            \
   logic                  bready;            \
   logic [IDBITS-1:0]     bid;               \
   logic [1:0]            bresp;             \
                                             \
   assign ``PREFIX``_ar_valid      = arvalid;             \
   assign arready                  = ``PREFIX``_ar_ready; \
   assign ``PREFIX``_ar_bits_id    = arid;                \
   assign ``PREFIX``_ar_bits_addr  = araddr;              \
   assign ``PREFIX``_ar_bits_len   = arlen;               \
   assign ``PREFIX``_ar_bits_size  = arsize;              \
   assign ``PREFIX``_ar_bits_burst = arburst;             \
   assign ``PREFIX``_ar_bits_lock  = arlock;              \
   assign ``PREFIX``_ar_bits_cache = arcache;             \
   assign ``PREFIX``_ar_bits_prot  = arprot;              \
   assign ``PREFIX``_ar_bits_qos   = arqos;               \
                                                          \
   assign ``PREFIX``_aw_valid      = awvalid;             \
   assign awready                  = ``PREFIX``_aw_ready; \
   assign ``PREFIX``_aw_bits_id    = awid;                \
   assign ``PREFIX``_aw_bits_addr  = awaddr;              \
   assign ``PREFIX``_aw_bits_len   = awlen;               \
   assign ``PREFIX``_aw_bits_size  = awsize;              \
   assign ``PREFIX``_aw_bits_burst = awburst;             \
   assign ``PREFIX``_aw_bits_lock  = awlock;              \
   assign ``PREFIX``_aw_bits_cache = awcache;             \
   assign ``PREFIX``_aw_bits_prot  = awprot;              \
   assign ``PREFIX``_aw_bits_qos   = awqos;               \
                                                          \
   assign rvalid                = ``PREFIX``_r_valid;     \
   assign ``PREFIX``_r_ready    = rready;                 \
   assign rid                   = ``PREFIX``_r_bits_id;   \
   assign rdata                 = ``PREFIX``_r_bits_data; \
   assign rresp                 = ``PREFIX``_r_bits_resp; \
   assign rlast                 = ``PREFIX``_r_bits_last; \
                                                          \
   assign ``PREFIX``_w_valid     = wvalid;                \
   assign wready                 = ``PREFIX``_w_ready;    \
   assign ``PREFIX``_w_bits_data = wdata;                 \
   assign ``PREFIX``_w_bits_strb = wstrb;                 \
   assign ``PREFIX``_w_bits_last = wlast;                 \
                                                          \
   assign bvalid                = ``PREFIX``_b_valid;     \
   assign ``PREFIX``_b_ready    = bready;                 \
   assign bid                   = ``PREFIX``_b_bits_id;   \
   assign bresp                 = ``PREFIX``_b_bits_resp;














//
