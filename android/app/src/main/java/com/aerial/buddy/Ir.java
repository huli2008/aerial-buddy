package com.aerial.buddy;

import android.content.Context;
import android.hardware.ConsumerIrManager;

/**
 * 红外发射：NEC 编码 + 自动适配手机红外能力。
 *
 * <p>不同品牌手机（小米/红米、华为、荣耀、乐视、努比亚、部分三星）红外驱动差异很大：
 * 有的只支持有限载波（如 36k–40k），有的对 pattern 长度/奇偶敏感，有的限频段。
 * 这里统一做：查询载波范围 → 选最接近 38 kHz 的可用频率 → 必要时拆帧/补偶 → 失败再降级重试。
 */
public final class Ir {

    private Ir() {
    }

    /** 手机有没有红外发射器；没有返回 null。 */
    public static ConsumerIrManager emitter(Context ctx) {
        try {
            ConsumerIrManager ir = (ConsumerIrManager) ctx.getSystemService(Context.CONSUMER_IR_SERVICE);
            if (ir != null && ir.hasIrEmitter()) return ir;
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 选择手机支持的、最接近 38 kHz 的载波；查不到就用 38 kHz。 */
    public static int carrier(ConsumerIrManager ir) {
        try {
            ConsumerIrManager.CarrierFrequencyRange[] ranges = ir.getCarrierFrequencies();
            if (ranges == null || ranges.length == 0) return 38000;
            int best = -1, bestDist = Integer.MAX_VALUE;
            for (ConsumerIrManager.CarrierFrequencyRange r : ranges) {
                for (int f : new int[]{38000, 36000, 40000}) {
                    if (f >= r.getMinFrequency() && f <= r.getMaxFrequency()) {
                        int d = Math.abs(f - 38000);
                        if (d < bestDist) {
                            best = f;
                            bestDist = d;
                        }
                    }
                }
            }
            return best > 0 ? best : 38000;
        } catch (Exception e) {
            return 38000;
        }
    }

    /** 载波范围的文字描述，用于页面展示。 */
    public static String describe(ConsumerIrManager ir) {
        try {
            ConsumerIrManager.CarrierFrequencyRange[] ranges = ir.getCarrierFrequencies();
            if (ranges == null || ranges.length == 0) return "未报告载波范围（按 38 kHz 发送）";
            StringBuilder sb = new StringBuilder();
            for (ConsumerIrManager.CarrierFrequencyRange r : ranges) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(r.getMinFrequency() / 1000).append("–").append(r.getMaxFrequency() / 1000).append(" kHz");
            }
            return "支持载波 " + sb;
        } catch (Exception e) {
            return "读取载波范围失败（按 38 kHz 发送）";
        }
    }

    /** 标准 NEC 帧：addr(8) ~addr(8) cmd(8) ~cmd(8)，LSB 先发；末尾补一拍保证偶数长度。 */
    public static int[] nec(int addr, int cmd) {
        int[] bits = new int[32];
        int a = addr & 0xff, c = cmd & 0xff;
        for (int i = 0; i < 8; i++) bits[i] = (a >> i) & 1;
        for (int i = 0; i < 8; i++) bits[8 + i] = ((~a) >> i) & 1;
        for (int i = 0; i < 8; i++) bits[16 + i] = (c >> i) & 1;
        for (int i = 0; i < 8; i++) bits[24 + i] = ((~c) >> i) & 1;

        int[] pattern = new int[2 + 64 + 1];
        pattern[0] = 9000;
        pattern[1] = 4500;
        int pos = 2;
        for (int i = 0; i < 32; i++) {
            pattern[pos++] = 560;
            pattern[pos++] = bits[i] == 1 ? 1690 : 560;
        }
        pattern[pos] = 560;
        return pattern;
    }

    /**
     * 发送一帧：先按手机支持的载波发；失败则尝试 38k / 36k / 40k 与「重复帧」形态。
     *
     * @return 实际成功的载波频率，全部失败返回 -1
     */
    public static int send(ConsumerIrManager ir, int addr, int cmd) {
        int[] pattern = nec(addr, cmd);
        int preferred = carrier(ir);
        int[] freqs = new int[]{preferred, 38000, 36000, 40000};
        for (int f : freqs) {
            if (tryTransmit(ir, f, pattern)) return f;
        }
        // 有的驱动要求偶数长度，补一个尾部空闲再试
        int[] even = new int[pattern.length + 1];
        System.arraycopy(pattern, 0, even, 0, pattern.length);
        even[pattern.length] = 20000;
        for (int f : freqs) {
            if (tryTransmit(ir, f, even)) return f;
        }
        return -1;
    }

    private static boolean tryTransmit(ConsumerIrManager ir, int freq, int[] pattern) {
        try {
            ir.transmit(freq, pattern);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 常见品牌候选 NEC 码（地址, 命令）。 */
    public static final String[] BRANDS = {
            "TCL / 雷鸟", "小米 / 红米", "海信", "创维", "索尼", "长虹", "康佳", "三星", "LG", "飞利浦"
    };

    /** 自动扫描用的地址列表（NEC 常见电视地址）。 */
    public static int[] scanAddresses() {
        return new int[]{0x00, 0x01, 0x02, 0x04, 0x06, 0x07, 0x08, 0x0A, 0x0C, 0x0E,
                0x10, 0x11, 0x14, 0x18, 0x1C, 0x20, 0x28, 0x2A, 0x30, 0x3C, 0x40, 0x48, 0x50};
    }

    /** 自动扫描用的命令列表（电源键常见值）。 */
    public static int[] scanCommands() {
        return new int[]{0x08, 0x0C, 0x02, 0x15, 0x0D, 0x1A};
    }

    public static int[][] powerCodes(int brand) {
        switch (brand) {
            case 0:
                return new int[][]{{0x04, 0x08}, {0x04, 0x0C}, {0x40, 0x08}, {0x00, 0x0C}, {0x04, 0x02}};
            case 1:
                return new int[][]{{0x28, 0x0C}, {0x28, 0x08}, {0x0C, 0x08}, {0x00, 0x0C}};
            case 2:
                return new int[][]{{0x00, 0x0C}, {0x04, 0x08}, {0x08, 0x0C}, {0x00, 0x08}};
            case 3: // 创维
                return new int[][]{{0x00, 0x0C}, {0x11, 0x0C}, {0x04, 0x0C}, {0x3C, 0x0C}};
            case 4: // 索尼
                return new int[][]{{0x01, 0x15}, {0x01, 0x0C}, {0x00, 0x0C}, {0x04, 0x08}};
            case 5: // 长虹
                return new int[][]{{0x02, 0x0C}, {0x00, 0x0C}, {0x04, 0x0C}, {0x0A, 0x08}};
            case 6: // 康佳
                return new int[][]{{0x00, 0x08}, {0x04, 0x0C}, {0x08, 0x0C}, {0x0C, 0x0C}};
            case 7: // 三星
                return new int[][]{{0x07, 0x02}, {0x07, 0x0C}, {0x00, 0x02}, {0x02, 0x02}};
            case 8: // LG
                return new int[][]{{0x04, 0x08}, {0x00, 0x08}, {0x04, 0x0C}, {0x20, 0x08}};
            default: // 飞利浦
                return new int[][]{{0x00, 0x0C}, {0x0C, 0x0C}, {0x04, 0x08}, {0x10, 0x08}};
        }
    }
}
