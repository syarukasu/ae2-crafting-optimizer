package com.syaru.ae2craftingoptimizer.golden;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

record CanonicalCraftingResult(
        String fixtureId,
        String rootOutput,
        BigInteger requestedAmount,
        String status,
        Map<String, String> selectedConcreteKeys,
        Map<String, BigInteger> patternExecutions,
        Map<String, BigInteger> usedItems,
        Map<String, BigInteger> emittedOutputs,
        Map<String, BigInteger> remainingItems,
        Map<String, BigInteger> missingItems,
        Map<String, BigInteger> finalOwnershipTotals,
        BigInteger reservedBytes,
        String programFingerprint) {
    CanonicalCraftingResult {
        selectedConcreteKeys = immutableStrings(selectedConcreteKeys);
        patternExecutions = immutableNumbers(patternExecutions);
        usedItems = immutableNumbers(usedItems);
        emittedOutputs = immutableNumbers(emittedOutputs);
        remainingItems = immutableNumbers(remainingItems);
        missingItems = immutableNumbers(missingItems);
        finalOwnershipTotals = immutableNumbers(finalOwnershipTotals);
        if (reservedBytes.signum() < 0) {
            throw new IllegalArgumentException("reserved bytes must not be negative");
        }
    }

    String payloadDigest() {
        return sha256(canonicalJson(false));
    }

    String toCanonicalJson() {
        return canonicalJson(true);
    }

    private String canonicalJson(boolean includeDigest) {
        StringBuilder json = new StringBuilder(512);
        json.append('{')
                .append("\"fixtureId\":").append(string(fixtureId))
                .append(",\"rootOutput\":").append(string(rootOutput))
                .append(",\"requestedAmount\":").append(string(requestedAmount.toString()))
                .append(",\"status\":").append(string(status));
        appendStrings(json, "selectedConcreteKeys", selectedConcreteKeys);
        appendNumbers(json, "patternExecutions", patternExecutions);
        appendNumbers(json, "usedItems", usedItems);
        appendNumbers(json, "emittedOutputs", emittedOutputs);
        appendNumbers(json, "remainingItems", remainingItems);
        appendNumbers(json, "missingItems", missingItems);
        appendNumbers(json, "finalOwnershipTotals", finalOwnershipTotals);
        json.append(",\"reservedBytes\":").append(string(reservedBytes.toString()))
                .append(",\"programFingerprint\":").append(string(programFingerprint));
        if (includeDigest) {
            json.append(",\"payloadDigest\":").append(string(payloadDigest()));
        }
        return json.append('}').toString();
    }

    private static void appendStrings(StringBuilder json, String name, Map<String, String> values) {
        json.append(',').append(string(name)).append(':').append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(string(entry.getKey())).append(':').append(string(entry.getValue()));
        }
        json.append('}');
    }

    private static void appendNumbers(
            StringBuilder json,
            String name,
            Map<String, BigInteger> values) {
        json.append(',').append(string(name)).append(':').append('{');
        boolean first = true;
        for (Map.Entry<String, BigInteger> entry : values.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(string(entry.getKey())).append(':').append(string(entry.getValue().toString()));
        }
        json.append('}');
    }

    private static Map<String, String> immutableStrings(Map<String, String> values) {
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }

    private static Map<String, BigInteger> immutableNumbers(Map<String, BigInteger> values) {
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }

    private static String string(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte valueByte : digest) {
                result.append(String.format("%02x", valueByte & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
