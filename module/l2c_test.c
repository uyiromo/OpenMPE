// See LICENSE for license details.
#include <linux/init.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/device.h>
#include <linux/platform_device.h>
#include <linux/interrupt.h>
#include <linux/io.h>
#include <linux/semaphore.h>
#include <linux/of_address.h>

int g_irq;
static int l2c_probe(struct platform_device *pdev);
static int l2c_remove(struct platform_device *pdev);

static const struct of_device_id l2c_of_match[] = {
	{ .compatible = "sifive,inclusivecache0", },
	{ /* end of table */ },
};

static struct platform_driver l2c_driver = {
    .probe  = l2c_probe,
    .remove = l2c_remove,
	.driver = {
		.name = "l2c test driver",
		.owner = THIS_MODULE,
		.of_match_table = l2c_of_match,
	},
};
MODULE_DEVICE_TABLE(of, l2c_of_match);

static irqreturn_t
l2c_interrupt(int irq, void *dev_id)
{
	if (irq == g_irq) {
		pr_info("*** detected intettupt\n");
		return IRQ_HANDLED;
	}
	return IRQ_NONE;
}



static int
l2c_probe(struct platform_device *pdev)
{
	int ret;
	struct resource *r;
	int irq;

	pr_info("*** l2c_probe enter\n");

    // Show my device tree information
    r = platform_get_resource(pdev, IORESOURCE_MEM, 0);
    if (r != NULL) {
        pr_info("*** reg  = [0x%08lx - 0x%08lx]\n", (unsigned long)r->start, (unsigned long)r->end);
        pr_info("*** name = %s\n", r->name);
    } else {
        pr_err("*** FAILED TO GET DEVICE TREE INFORMATION!!!\n");
        goto bad_result;
    }


    // Get irq
    irq = platform_get_irq(pdev, 0);
    g_irq = irq;
    if (irq < 0) {
        pr_err("*** FAILED TO GET IRQ\n");
        goto bad_result;
    } else {
        pr_info("*** irq = %d\n", irq);
    }


    // Register handler to irq
    ret = request_irq(irq, l2c_interrupt, 0, l2c_driver.driver.name, &l2c_driver);
    if(ret) {
        pr_err("*** FAILED TO REGISTER HANDLER !!!\n");
        goto bad_result;
    }


bad_result:
	pr_info("*** l2c_probe exit\n");
	return ret;
}


static int
l2c_remove(struct platform_device *pdev)
{
	pr_info("*** l2c_remove enter\n");

    free_irq(g_irq, &l2c_driver);

    pr_info("*** l2c_remove exit\n");
	return 0;
}


MODULE_LICENSE("GPL");

static int __init
l2c_init(void)
{
	int ret;

	pr_info("*** l2c_init enter\n");
	ret = platform_driver_register(&l2c_driver);
	if (ret != 0) {
		pr_err("platform_driver_register returned %d\n", ret);
		return ret;
	}

	pr_info("*** l2c_init exit\n");
	return 0;
}

static void
l2c_exit(void)
{
	pr_info("*** l2c_exit enter\n");
	platform_driver_unregister(&l2c_driver);
	pr_info("*** l2c_exit exit\n");
}


module_init(l2c_init);
module_exit(l2c_exit);
