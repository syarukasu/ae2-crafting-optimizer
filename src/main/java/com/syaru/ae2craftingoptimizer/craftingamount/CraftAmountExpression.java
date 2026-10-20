/*
 * Expression grammar and shunting-yard precedence adapted from
 * Applied Energistics 2 MathExpressionParser, Copyright (c) TeamAppliedEnergistics.
 * Distributed under the GNU Lesser General Public License, version 3 or later.
 */
package com.syaru.ae2craftingoptimizer.craftingamount;

import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.ParsePosition;
import java.util.ArrayDeque;
import java.util.Optional;

/** Exact arithmetic for the crafting amount field, not a global AE2 parser replacement. */
public final class CraftAmountExpression {
    public static final int MAXIMUM_CHARACTERS = BigCountMath.HARD_MAXIMUM_DECIMAL_DIGITS + 1;

    private CraftAmountExpression() { }

    public static Optional<BigInteger> parse(String text, DecimalFormat format, int amountPerUnit,
            int maximumBits) {
        if (amountPerUnit <= 0) return Optional.empty();
        return evaluate(text, format).flatMap(value -> {
            try {
                if (value.numerator().signum() <= 0
                        || (amountPerUnit == 1 && !value.denominator().equals(BigInteger.ONE))) {
                    return Optional.empty();
                }
                var scaled = value.multiply(Rational.of(BigInteger.valueOf(amountPerUnit)));
                var division = scaled.numerator().divideAndRemainder(scaled.denominator());
                var amount = division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
                return Optional.of(BigCountMath.requireMaximumBits(amount, "craft amount", maximumBits));
            } catch (ArithmeticException | IllegalArgumentException invalid) {
                return Optional.empty();
            }
        });
    }

    public static Optional<Rational> evaluate(String text, DecimalFormat format) {
        if (text == null || text.length() > MAXIMUM_CHARACTERS) return Optional.empty();
        if (text.startsWith("=")) text = text.substring(1);
        var numbers = new ArrayDeque<Rational>();
        var operators = new ArrayDeque<Character>();
        boolean operand = true;
        try {
            for (int i = 0; i < text.length();) {
                char current = text.charAt(i);
                if (Character.isWhitespace(current)) { i++; continue; }
                if (operand && current != '-' && current != '+' && current != '(') {
                    var position = new ParsePosition(i);
                    var parsed = format.parse(text, position);
                    if (!(parsed instanceof BigDecimal decimal) || position.getIndex() <= i) {
                        return Optional.empty();
                    }
                    numbers.push(Rational.decimal(decimal));
                    operand = false;
                    i = position.getIndex();
                    continue;
                }
                if (operand && (current == '-' || current == '+')) {
                    if (current == '-') operators.push('u');
                } else if (operand && current == '(') {
                    operators.push(current);
                } else if (!operand && current == ')') {
                    while (!operators.isEmpty() && operators.peek() != '(') {
                        apply(numbers, operators.pop());
                    }
                    if (operators.isEmpty()) return Optional.empty();
                    operators.pop();
                } else if (!operand && "+-*/^".indexOf(current) >= 0) {
                    while (!operators.isEmpty() && operators.peek() != '('
                            && precedence(operators.peek()) <= precedence(current)) {
                        apply(numbers, operators.pop());
                    }
                    operators.push(current);
                    operand = true;
                } else {
                    return Optional.empty();
                }
                i++;
            }
            if (operand) return Optional.empty();
            while (!operators.isEmpty()) apply(numbers, operators.pop());
            return numbers.size() == 1 ? Optional.of(numbers.pop()) : Optional.empty();
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private static int precedence(char operator) {
        return switch (operator) {
            case '^' -> -1;
            case 'u' -> 0;
            case '*', '/' -> 1;
            case '+', '-' -> 2;
            default -> throw new IllegalArgumentException("invalid operator");
        };
    }

    private static void apply(ArrayDeque<Rational> values, char operator) {
        int needed = operator == 'u' ? 1 : 2;
        if (values.size() < needed) throw new IllegalArgumentException("missing operand");
        var right = values.pop();
        if (operator == 'u') { values.push(right.negate()); return; }
        var left = values.pop();
        values.push(switch (operator) {
            case '+' -> left.add(right);
            case '-' -> left.add(right.negate());
            case '*' -> left.multiply(right);
            case '/' -> left.multiply(new Rational(right.denominator(), right.numerator()));
            case '^' -> left.pow(right);
            default -> throw new IllegalArgumentException("unmatched bracket");
        });
    }

    public record Rational(BigInteger numerator, BigInteger denominator) {
        public Rational {
            if (denominator.signum() == 0) throw new ArithmeticException("division by zero");
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            var gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
            bounded(numerator);
            bounded(denominator);
        }

        public static Rational of(BigInteger value) { return new Rational(value, BigInteger.ONE); }

        static Rational decimal(BigDecimal value) {
            int scale = value.scale();
            if (Math.abs((long) scale) > BigCountMath.HARD_MAXIMUM_DECIMAL_DIGITS) {
                throw new IllegalArgumentException("decimal scale exceeds count contract");
            }
            return scale < 0
                    ? of(value.unscaledValue().multiply(BigInteger.TEN.pow(-scale)))
                    : new Rational(value.unscaledValue(), BigInteger.TEN.pow(scale));
        }

        public Rational negate() { return new Rational(numerator.negate(), denominator); }

        public Rational add(Rational other) {
            return new Rational(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }

        public Rational multiply(Rational other) {
            return new Rational(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
        }

        Rational pow(Rational exponent) {
            if (!exponent.denominator.equals(BigInteger.ONE) || exponent.numerator.signum() < 0) {
                throw new IllegalArgumentException("non-negative integer exponent required");
            }
            return new Rational(power(numerator, exponent.numerator), power(denominator, exponent.numerator));
        }

        private static BigInteger power(BigInteger value, BigInteger exponent) {
            if (exponent.signum() == 0) return BigInteger.ONE;
            if (value.signum() == 0) return BigInteger.ZERO;
            if (value.abs().equals(BigInteger.ONE)) {
                return value.signum() < 0 && exponent.testBit(0) ? BigInteger.ONE.negate() : BigInteger.ONE;
            }
            int count = exponent.intValueExact();
            // Lower bound on result bits prevents allocating an out-of-contract power.
            if ((long) (value.abs().bitLength() - 1) * count >= BigCountMath.HARD_MAXIMUM_BITS) {
                throw new IllegalArgumentException("power exceeds count contract");
            }
            var result = value.pow(count);
            bounded(result);
            return result;
        }

        private static void bounded(BigInteger value) {
            // Intermediates use the bit budget, not the final decimal count limit:
            // 10^16384-1 must be evaluable even though 10^16384 is not a valid order.
            if (value.abs().bitLength() > BigCountMath.HARD_MAXIMUM_BITS) {
                throw new IllegalArgumentException("expression intermediate exceeds count bit budget");
            }
        }
    }
}
