package com.syaru.ae2craftingoptimizer.engine;

public final class CountOverflowException extends ArithmeticException {
    public CountOverflowException(String operation, long left, long right, String context) {
        super("Crafting count overflow during " + operation + " (" + left + ", " + right + ") at " + context);
    }
}
