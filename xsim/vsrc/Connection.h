`define TREE(N)                                   \
    logic         io_tree_##N##_master_ready;     \
    logic         io_tree_##N##_master_valid;     \
    logic [  3:0] io_tree_##N##_master_bits_id;   \
    logic [ 31:0] io_tree_##N##_master_bits_addr; \
    logic [511:0] io_tree_##N##_master_bits_data; \
    logic         io_tree_##N##_master_bits_rw;   \
    logic         io_tree_##N##_slave_ready;      \
    logic         io_tree_##N##_slave_valid;      \
    logic [  3:0] io_tree_##N##_slave_bits_id;    \
    logic [511:0] io_tree_##N##_slave_bits_data;  \
    logic [  1:0] io_tree_##N##_slave_bits_resp;  \
    logic         io_tree_##N##_slave_bits_rw;    \
                                                  \
    InternalBusSlave tree##N(                     \
        .clock(clock),                            \
        .reset(reset),                            \
        .mvalid(io_tree_##N##_master_valid),      \
        .mready(io_tree_##N##_master_ready),      \
        .mid(io_tree_##N##_master_bits_id),       \
        .maddr(io_tree_##N##_master_bits_addr),   \
        .mdata(io_tree_##N##_master_bits_data),   \
        .mrw(io_tree_##N##_master_bits_rw),       \
        .svalid(io_tree_##N##_slave_valid),       \
        .sready(io_tree_##N##_slave_ready),       \
        .sid(io_tree_##N##_slave_bits_id),        \
        .sdata(io_tree_##N##_slave_bits_data),    \
        .sresp(io_tree_##N##_slave_bits_resp),    \
        .srw(io_tree_##N##_slave_bits_rw),        \
    );

`define FE_TREE_CONNECTION(N)                                    \
    .io_tree_##N##master_ready(tree_##N##_master_ready),         \
    .io_tree_##N##master_valid(tree_##N##_master_valid),         \
    .io_tree_##N##master_bits_id(tree_##N##_master_bits_id),     \
    .io_tree_##N##master_bits_addr(tree_##N##_master_bits_addr), \
    .io_tree_##N##master_bits_data(tree_##N##_master_bits_data), \
    .io_tree_##N##master_bits_rw(tree_##N##_master_bits_rw),     \
    .io_tree_##N##slave_ready(tree_##N##_slave_ready),          \
    .io_tree_##N##slave_valid(tree_##N##_slave_valid),          \
    .io_tree_##N##slave_bits_id(tree_##N##_slave_bits_id),      \
    .io_tree_##N##slave_bits_data(tree_##N##_slave_bits_data),  \
    .io_tree_##N##slave_bits_resp(tree_##N##_slave_bits_resp),  \
    .io_tree_##N##slave_bits_rw(tree_##N##_slave_bits_rw),      \

#define FRONTEND()                     \
    Frontend fe(                       \
      FE_TREE_CONNECTION(0),           \
      FE_TREE_CONNECTION(1),           \
      FE_TREE_CONNECTION(2),           \
      FE_TREE_CONNECTION(3),           \
      FE_TREE_CONNECTION(4),           \
      FE_TREE_CONNECTION(5),           \
      FE_TREE_CONNECTION(6),           \
      FE_TREE_CONNECTION(7),           \
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
      .io_cpu_w_weady(wready),         \
      .io_cpu_w_bits_data(wdata),      \
      .io_cpu_w_bits_strb(wstrb),      \
      .io_cpu_w_bits_last(wlast),      \
      .io_cpu_b_valid(bvalid),         \
      .io_cpu_b_weady(bready),         \
      .io_cpu_b_bits_id(bid),          \
      .io_cpu_b_bits_resp(bresp)       \
    );
























//
