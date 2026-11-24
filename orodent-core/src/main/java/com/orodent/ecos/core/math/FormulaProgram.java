package com.orodent.ecos.core.math;

import java.math.*;
import java.util.*;

/** Bounded arithmetic expressions, without scripting or access to application objects. */
public final class FormulaProgram {
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal PI = new BigDecimal("3.141592653589793238462643383279502884");
    private static final Set<String> INPUTS = Set.of("D", "Hmedia", "P", "PI", "pi");

    private record Step(int line, String name, Node expression) {}

    private interface Node {
        BigDecimal value(Map<String, BigDecimal> values);
    }

    private final List<Step> steps;

    private FormulaProgram(List<Step> steps) {
        this.steps = List.copyOf(steps);
    }

    public static FormulaProgram compile(String source) {
        if (source == null || source.length() > 16000)
            throw new IllegalArgumentException("Formule: massimo 16000 caratteri.");
        var known = new HashSet<>(INPUTS);
        var steps = new ArrayList<Step>();
        String[] lines = source.split("\\R", -1);
        if (lines.length > 200) throw new IllegalArgumentException("Formule: massimo 200 righe.");
        for (int index = 0; index < lines.length; index++) {
            int lineNumber = index + 1;
            String line = lines[index].split("#", 2)[0].strip();
            if (line.isEmpty()) continue;
            try {
                int equals = line.indexOf('=');
                if (equals < 0 || equals != line.lastIndexOf('='))
                    throw new IllegalArgumentException(
                            "usa una sola assegnazione, per esempio D + Hmedia = a1.");
                String left = line.substring(0, equals).strip(),
                        right = line.substring(equals + 1).strip();
                // Accept both the requested expression = result and result = expression.
                boolean resultRight =
                        identifier(right) && (!identifier(left) || known.contains(left));
                String name = resultRight ? right : left;
                String expression = resultRight ? left : right;
                if (!identifier(name))
                    throw new IllegalArgumentException("nome risultato non valido.");
                if (known.contains(name))
                    throw new IllegalArgumentException(
                            "la variabile " + name + " esiste già e non può essere sovrascritta.");
                Node parsed = new Parser(expression, known).parse();
                steps.add(new Step(lineNumber, name, parsed));
                known.add(name);
            } catch (IllegalArgumentException failure) {
                throw error(lineNumber, failure.getMessage());
            }
        }
        if (steps.isEmpty()) throw new IllegalArgumentException("Inserisci almeno una formula.");
        return new FormulaProgram(steps);
    }

    public List<String> outputs() {
        return steps.stream().map(Step::name).toList();
    }

    public Map<String, BigDecimal> evaluate(
            BigDecimal diameter, BigDecimal meanHeight, BigDecimal weight) {
        var values = new LinkedHashMap<String, BigDecimal>();
        values.put("D", input(diameter, "Diametro"));
        values.put("Hmedia", input(meanHeight, "Altezza media"));
        values.put("P", input(weight, "Peso"));
        values.put("PI", PI);
        values.put("pi", PI);
        var outputs = new LinkedHashMap<String, BigDecimal>();
        for (Step step : steps) {
            try {
                BigDecimal value = bounded(step.expression().value(values));
                values.put(step.name(), value);
                outputs.put(step.name(), value);
            } catch (ArithmeticException | IllegalArgumentException failure) {
                throw error(
                        step.line(),
                        failure instanceof ArithmeticException
                                ? "operazione non valida o divisione per zero."
                                : failure.getMessage());
            }
        }
        return Collections.unmodifiableMap(outputs);
    }

    private static boolean identifier(String name) {
        return name.matches("[A-Za-z][A-Za-z0-9_]{0,31}");
    }

    private static IllegalArgumentException error(int line, String message) {
        return new IllegalArgumentException("Riga " + line + ": " + message);
    }

    private static BigDecimal input(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0)
            throw new IllegalArgumentException(label + ": inserisci un valore positivo.");
        return bounded(value);
    }

    private static BigDecimal bounded(BigDecimal value) {
        if (value.precision() - value.scale() > 100 || value.scale() > 1000)
            throw new IllegalArgumentException("risultato fuori dall'intervallo di calcolo.");
        return value.round(MC);
    }

    private static final class Parser {
        final String text;
        final Set<String> known;
        int position, depth, tokens;

        Parser(String text, Set<String> known) {
            this.text = text;
            this.known = known;
        }

        Node parse() {
            Node result = sum();
            space();
            if (position != text.length()) fail("carattere inatteso: " + text.charAt(position));
            return result;
        }

        Node sum() {
            Node value = product();
            while (true) {
                if (take('+')) {
                    Node a = value, b = product();
                    value = v -> bounded(a.value(v).add(b.value(v), MC));
                } else if (take('-')) {
                    Node a = value, b = product();
                    value = v -> bounded(a.value(v).subtract(b.value(v), MC));
                } else return value;
            }
        }

        Node product() {
            Node value = unary();
            while (true) {
                if (take('*')) {
                    Node a = value, b = unary();
                    value = v -> bounded(a.value(v).multiply(b.value(v), MC));
                } else if (take('/')) {
                    Node a = value, b = unary();
                    value = v -> bounded(a.value(v).divide(b.value(v), MC));
                } else return value;
            }
        }

        Node unary() {
            enter();
            try {
                if (take('+')) return unary();
                if (take('-')) {
                    Node value = unary();
                    return v -> value.value(v).negate(MC);
                }
                Node value = atom();
                if (take('^')) {
                    Node base = value, exponent = unary();
                    return v -> power(base.value(v), exponent.value(v));
                }
                return value;
            } finally {
                depth--;
            }
        }

        Node atom() {
            space();
            if (++tokens > 400) fail("formula troppo lunga.");
            if (take('(')) {
                Node value = sum();
                expect(')');
                return value;
            }
            if (position >= text.length()) {
                fail("manca un valore.");
            }
            char first = text.charAt(position);
            if (Character.isDigit(first) || first == '.' || first == ',') {
                int start = position;
                while (position < text.length() && Character.isDigit(text.charAt(position)))
                    position++;
                if (position < text.length()
                        && (text.charAt(position) == '.' || text.charAt(position) == ',')) {
                    position++;
                    while (position < text.length() && Character.isDigit(text.charAt(position)))
                        position++;
                }
                String number = text.substring(start, position).replace(',', '.');
                try {
                    BigDecimal value = bounded(new BigDecimal(number, MC));
                    return v -> value;
                } catch (NumberFormatException failure) {
                    fail("numero non valido.");
                }
            }
            int start = position;
            while (position < text.length()
                    && (Character.isLetterOrDigit(text.charAt(position))
                            || text.charAt(position) == '_')) position++;
            String name = text.substring(start, position);
            if (name.isEmpty()) {
                fail("valore o variabile attesi.");
            }
            if (take('(')) {
                Node argument = sum();
                expect(')');
                return switch (name) {
                    case "sqrt" -> v -> argument.value(v).sqrt(MC);
                    case "abs" -> v -> argument.value(v).abs(MC);
                    case "cbrt" -> v -> finite(StrictMath.cbrt(argument.value(v).doubleValue()));
                    default -> throw new IllegalArgumentException("funzione sconosciuta: " + name);
                };
            }
            if (!known.contains(name)) fail("variabile non disponibile: " + name);
            return values -> values.get(name);
        }

        boolean take(char c) {
            space();
            if (position < text.length() && text.charAt(position) == c) {
                position++;
                return true;
            }
            return false;
        }

        void expect(char c) {
            if (!take(c)) fail("manca '" + c + "'.");
        }

        void space() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position)))
                position++;
        }

        void enter() {
            if (++depth > 48) fail("formula troppo annidata.");
        }

        void fail(String message) {
            throw new IllegalArgumentException(message);
        }
    }

    private static BigDecimal power(BigDecimal base, BigDecimal exponent) {
        if (exponent.abs().compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("esponente troppo grande.");
        try {
            int n = exponent.intValueExact();
            if (n >= 0) return bounded(base.pow(n, MC));
            return bounded(BigDecimal.ONE.divide(base.pow(-n, MC), MC));
        } catch (ArithmeticException nonInteger) {
            if (base.signum() == 0 && exponent.signum() < 0)
                throw new IllegalArgumentException("divisione per zero.");
            return finite(StrictMath.pow(base.doubleValue(), exponent.doubleValue()));
        }
    }

    private static BigDecimal finite(double value) {
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("risultato non reale o fuori intervallo.");
        return bounded(BigDecimal.valueOf(value));
    }
}
