# See LICENSE for license details.

KERNEL_DIR    =
CROSS_COMPILE = riscv64-unknown-linux-gnu-
ARCH          = riscv

src   := l2c_test.c
obj-m := l2c_test.o
CFLAGS_MODULE := -fno-pic

GCC := $(shell which ${CROSS_COMPILE}gcc)

.PHONY: precheck clean

precheck:
ifeq ($(KERNEL_DIR),)
	$(error Invalid KERNEL_DIR: ${KERNEL_DIR})
endif
ifeq ($(GCC),)
	$(error Invalid GCC: ${CROSS_COMPILE}gcc)
endif
	@echo "Configuration:"
	@echo "  ARCH          = ${ARCH}"
	@echo "  CROSS_COMPILE = ${CROSS_COMPILE}"
	@echo "  GCC           = ${GCC}"
	@echo "  KERNEL_DIR    = ${KERNEL_DIR}"

all: precheck l2c_test.c
	make -C ${KERNEL_DIR} ARCH=${ARCH} CROSS_COMPILE=${CROSS_COMPILE} M=$(PWD) modules

clean: precheck
	make -C ${KERNEL_DIR} ARCH=${ARCH} CROSS_COMPILE=${CROSS_COMPILE} M=$(PWD) clean
