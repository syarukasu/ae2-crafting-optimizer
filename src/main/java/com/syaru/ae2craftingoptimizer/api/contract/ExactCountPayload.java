package com.syaru.ae2craftingoptimizer.api.contract;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable common payload used by requests, plans, hosts, journals, and receipts. */
public final class ExactCountPayload {
    private final PayloadKind kind;
    private final String identifier;
    private final NavigableMap<String, BigInteger> counts;
    private final byte[] digest;

    private ExactCountPayload(
            PayloadKind kind,
            String identifier,
            NavigableMap<String, BigInteger> counts,
            byte[] digest) {
        this.kind = kind;
        this.identifier = identifier;
        this.counts = Collections.unmodifiableNavigableMap(counts);
        this.digest = digest.clone();
    }

    public static ExactCountPayload of(
            PayloadKind kind,
            String identifier,
            Map<String, BigInteger> counts,
            byte[] digest,
            ExactCountLimits limits) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(counts, "counts");
        Objects.requireNonNull(limits, "limits");
        limits.validateIdentifier(identifier);
        limits.validateDigest(digest);
        limits.validateKeyCount(counts.size());
        NavigableMap<String, BigInteger> ordered = new TreeMap<>();
        for (Map.Entry<String, BigInteger> entry : counts.entrySet()) {
            limits.validateRequiredIdentifier(entry.getKey());
            limits.validateNonNegative(entry.getValue());
            if (ordered.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException("duplicate exact-count payload key");
            }
        }
        return new ExactCountPayload(kind, identifier, ordered, digest);
    }

    public PayloadKind kind() {
        return kind;
    }

    public String identifier() {
        return identifier;
    }

    /** Returns an immutable, lexicographically ordered view. */
    public Map<String, BigInteger> counts() {
        return counts;
    }

    public byte[] digest() {
        return digest.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExactCountPayload payload)) {
            return false;
        }
        return kind == payload.kind
                && identifier.equals(payload.identifier)
                && counts.equals(payload.counts)
                && Arrays.equals(digest, payload.digest);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(kind, identifier, counts);
        return 31 * result + Arrays.hashCode(digest);
    }
}
