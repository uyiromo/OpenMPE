# Runner for xsim

.PHONY: tb_Frontend clean
SLAVES = vsrc/InternalBusSlave.sv vsrc/AXI4BusSlave.sv

# verilog lists
AES128_V   = ../AES128Opt.v
CWMAC_V    = ../CWMACOpt.v ../GMulOpt.v ../GModOpt.v
FRONTEND_V = ../Frontend.v
BACKEND_V  = ../Backend.v ../FCFSArbiter.v
TREE_V     = ../Tree.v ../GModOpt.v ../GMulOpt.v
MPE_V      = ../MPE.v ../RRSkipArbiter.v ../GModOpt.v ../GMulOpt.v


tb_CWMACOpt:
	make -C ../ CWMACOpt.v
	iverilog -g2005-sv -I./vsrc -E vsrc/$@.sv -o $@.ex.sv
	xvlog -sv ${CWMAC_V} $@.ex.sv ${SLAVES}
	xelab --debug all --timescale 1ns/1ps $@
	xsim $@ --runall

genInitMem:
	make -C ../ CWMACOpt.v
	xvlog -sv ${CWMAC_V} vsrc/genInitMem.sv ${SLAVES}
	xelab --debug all --timescale 1ns/1ps $@
	xsim $@ --runall
	cp mem_init.txt ../../sim/


tb_AES128Opt:
	make -C ../ AES128Opt.v
	iverilog -g2005-sv -I./vsrc -E vsrc/$@.sv -o $@.ex.sv
	xvlog -sv ${AES128_V} $@.ex.sv ${SLAVES}
	xelab --debug all --timescale 1ns/1ps $@
	xsim $@ --runall

tb_Frontend:
	make -C ../ Frontend.v
	iverilog -g2005-sv -I./vsrc -E vsrc/$@.sv -o $@.ex.sv
	xvlog -sv ${FRONTEND_V} $@.ex.sv ${SLAVES}
	xelab --debug all --timescale 1ns/1ps $@
	xsim $@ --runall

tb_Backend:
	make -C ../ Backend.v
	iverilog -g2005-sv -I./vsrc -E vsrc/$@.sv -o $@.ex.sv
	xvlog -sv ${BACKEND_V} $@.ex.sv ${SLAVES}
	xelab --debug all --timescale 1ns/1ps $@
	xsim $@ --runall

tb_MPE:
	make -C ../ MPE.v
	iverilog -g2005-sv -I./vsrc -E vsrc/$@.sv -o $@.ex.sv
	xvlog -sv ${MPE_V} $@.ex.sv ${SLAVES}
	xelab --debug all --timescale 1ns/1ps $@
	xsim $@ --runall

clean:
	rm -fr xsim.dir *.jou *.pb *.log *.vcd *.wdb *.ex.sv
