package com.aerial.buddy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether an OCR'd payment screenshot looks like a real ¥10 transfer.
 *
 * <p>Rules: a success wording plus "10" that is either followed by 2 decimals or sits next to a
 * currency marker — otherwise a timestamp like 18:29:10 or an amount like 100.00 would slip through.
 * Heuristics only: a hand-made screenshot can still fool it, but casual re-use cannot.
 */
public final class ReceiptCheck {

    public static final int PRICE = 10;

    private static final String[] SUCCESS_WORDS = {
            "支付成功", "付款成功", "转账成功", "交易成功", "支付完成", "付款完成",
            "已支付", "已付款", "成功", "Completed", "Success", "Paid"
    };

    /** 10 / 10.0 / 10.00，且后面不能再跟数字（否则会匹配 100.00 的前两位）。 */
    private static final Pattern AMOUNT = Pattern.compile("(10(?:[.,]0{1,2})?)(?!\\d)");

    public static final class Result {
        public final boolean ok;
        public final String reason;

        Result(boolean ok, String reason) {
            this.ok = ok;
            this.reason = reason;
        }
    }

    private ReceiptCheck() {
    }

    public static Result check(String text) {
        if (text == null) return new Result(false, "识别失败：请换成清晰的支付成功截图");
        String flat = text.replaceAll("\\s+", "");
        if (flat.isEmpty()) return new Result(false, "截图里没有文字");

        boolean success = false;
        for (String w : SUCCESS_WORDS) {
            if (flat.contains(w)) {
                success = true;
                break;
            }
        }
        if (!success) return new Result(false, "没看到「支付成功」字样，请上传支付结果页截图");

        Matcher m = AMOUNT.matcher(flat);
        while (m.find()) {
            String num = m.group(1).replace(",", ".");
            int from = Math.max(0, m.start() - 3);
            int to = Math.min(flat.length(), m.end() + 3);
            String ctx = flat.substring(from, to);
            boolean currency = ctx.contains("¥") || ctx.contains("￥") || ctx.contains("元")
                    || ctx.contains("块") || ctx.contains("RMB") || ctx.contains("CNY");
            if (currency || num.contains(".")) return new Result(true, "校验通过");
        }
        return new Result(false, "没找到 ¥" + PRICE + " 的金额，请上传完整支付结果截图");
    }
}
