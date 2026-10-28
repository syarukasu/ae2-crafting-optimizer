package com.syaru.ae2craftingoptimizer.client;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.core.localization.GuiText;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

/** AE2のlong表示範囲を越える数量を、丸めによる上限張り付きなしで表示する。 */
public final class BigAmountFormatter {
    private static final BigInteger THOUSAND = BigInteger.valueOf(1_000L);

    private BigAmountFormatter() {
    }

    // Issue #224: FULL decimal text must not expand a tooltip to thousands of lines.
    public static boolean abbreviatesTooltip(BigInteger amount) {
        return amount.abs().toString().length() > 24;
    }

    public static String formatTooltip(AEKey key, BigInteger amount) {
        if (!abbreviatesTooltip(amount)) return format(key, amount, AmountFormat.FULL);
        String text = format(key, amount, AmountFormat.SLOT);
        String unit = key.getUnitSymbol();
        return unit == null ? text : text + " " + unit;
    }

    public static String exactCountsForClipboard(String name, BigInteger stored,
            BigInteger missing, BigInteger craft) {
        return name + "\nStored (raw units): " + stored
                + "\nMissing (raw units): " + missing
                + "\nTo craft (raw units): " + craft;
    }

    public static String format(AEKey key, BigInteger amount, AmountFormat format) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(format, "format");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }

        if (format == AmountFormat.FULL) {
            return formatFull(key, amount);
        }
        return formatCompact(amount, key.getAmountPerUnit());
    }

    /**
     * 4桁以下は整数、以降は3桁の有効数字を持つ科学表記にする。
     * EをExa接尾辞と誤認しないよう、指数記号は小文字eを使う。
     */
    public static String formatCompact(BigInteger amount) {
        return formatCompact(amount, 1);
    }

    // Issue #224: shorten only presentation; the field and request retain every digit.
    public static Component formatAmountTooltip(BigDecimal amount, DecimalFormat decimalFormat) {
        BigDecimal normalized = amount.stripTrailingZeros();
        int digits = Math.max(1, normalized.precision() - normalized.scale());
        if (digits <= 24) {
            return Component.literal("= " + decimalFormat.format(amount));
        }
        return Component.translatable("aco.tooltip.large_amount",
                Component.literal("= " + formatCompact(normalized)), digits);
    }

    // Issue #224: preserve wrappers and styles when another mod rewrites the title.
    public static Component formatPlanTitle(Component original, BigInteger usedBytes) {
        var contents = original.getContents();
        MutableComponent replacement = null;
        if (contents instanceof TranslatableContents translated) {
            Object[] args = translated.getArgs().clone();
            boolean changed = false;
            if (translated.getKey().equals(GuiText.BytesUsed.getTranslationKey())) {
                args = new Object[] {formatCompact(usedBytes)};
                changed = true;
            } else {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Component nested) {
                        Component formatted = formatPlanTitle(nested, usedBytes);
                        changed |= formatted != nested;
                        args[i] = formatted;
                    }
                }
            }
            if (changed) {
                replacement = MutableComponent.create(new TranslatableContents(
                        translated.getKey(), translated.getFallback(), args));
            }
        }
        for (int i = 0; i < original.getSiblings().size(); i++) {
            Component sibling = original.getSiblings().get(i);
            Component formatted = formatPlanTitle(sibling, usedBytes);
            if (formatted != sibling && replacement == null) {
                replacement = original.plainCopy();
                for (int j = 0; j < i; j++) replacement.append(original.getSiblings().get(j));
            }
            if (replacement != null) replacement.append(formatted);
        }
        if (replacement == null) return original;
        return replacement.setStyle(original.getStyle());
    }

    private static String formatCompact(BigInteger amount, int amountPerUnit) {
        BigDecimal units = new BigDecimal(amount)
                .divide(BigDecimal.valueOf(Math.max(1, amountPerUnit)), 6, RoundingMode.DOWN)
                .stripTrailingZeros();
        return formatCompact(units);
    }

    private static String formatCompact(BigDecimal units) {
        BigInteger wholeUnits = units.toBigInteger();
        if (units.scale() <= 0 && wholeUnits.compareTo(THOUSAND) < 0) {
            return wholeUnits.toString();
        }

        int exponent = units.precision() - units.scale() - 1;
        BigDecimal mantissa = units.movePointLeft(exponent)
                .setScale(2, RoundingMode.DOWN)
                .stripTrailingZeros();
        return mantissa.toPlainString() + "e" + exponent;
    }

    private static String formatFull(AEKey key, BigInteger amount) {
        int amountPerUnit = Math.max(1, key.getAmountPerUnit());
        BigDecimal units = new BigDecimal(amount)
                .divide(BigDecimal.valueOf(amountPerUnit), 3, RoundingMode.DOWN)
                .stripTrailingZeros();

        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());
        numberFormat.setGroupingUsed(true);
        numberFormat.setMaximumFractionDigits(3);
        numberFormat.setMinimumFractionDigits(0);
        String text = numberFormat.format(units);
        String unit = key.getUnitSymbol();
        return unit == null ? text : text + " " + unit;
    }
}
