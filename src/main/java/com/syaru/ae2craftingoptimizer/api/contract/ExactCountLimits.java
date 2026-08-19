package com.syaru.ae2craftingoptimizer.api.contract;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The single finite boundary shared by exact-count requests and persistence payloads.
 *
 * <p>The default count limit is intentionally the existing request boundary of 1,048,576
 * bits. Its canonical magnitude is 131,072 bytes, so it is persisted without a narrower
 * codec-specific truncation boundary.</p>
 */
public final class ExactCountLimits {
    public static final int DEFAULT_MAXIMUM_COUNT_BITS = 1_048_576;
    public static final int DEFAULT_MAXIMUM_CANONICAL_BYTES = 131_072;
    public static final int DEFAULT_MAXIMUM_KEYS_PER_PAYLOAD = 65_536;
    public static final int DEFAULT_MAXIMUM_ENCODED_PAYLOAD_BYTES = 16 * 1024 * 1024;
    public static final int DEFAULT_MAXIMUM_IDENTIFIER_LENGTH = 256;
    public static final int DEFAULT_MAXIMUM_DIGEST_LENGTH = 128;

    private final int maximumCountBits;
    private final int maximumCanonicalBytes;
    private final int maximumKeysPerPayload;
    private final int maximumEncodedPayloadBytes;
    private final int maximumIdentifierLength;
    private final int maximumDigestLength;

    public ExactCountLimits(
            int maximumCountBits,
            int maximumCanonicalBytes,
            int maximumKeysPerPayload,
            int maximumEncodedPayloadBytes,
            int maximumIdentifierLength,
            int maximumDigestLength) {
        if (maximumCountBits < 1) {
            throw new IllegalArgumentException("maximumCountBits must be positive");
        }
        if (maximumCanonicalBytes < 1) {
            throw new IllegalArgumentException("maximumCanonicalBytes must be positive");
        }
        if ((long) maximumCanonicalBytes * 8L < maximumCountBits) {
            throw new IllegalArgumentException(
                    "maximumCanonicalBytes cannot store maximumCountBits");
        }
        if (maximumKeysPerPayload < 0) {
            throw new IllegalArgumentException("maximumKeysPerPayload must not be negative");
        }
        if (maximumEncodedPayloadBytes < maximumCanonicalBytes) {
            throw new IllegalArgumentException(
                    "maximumEncodedPayloadBytes must include one maximum count");
        }
        if (maximumIdentifierLength < 1) {
            throw new IllegalArgumentException("maximumIdentifierLength must be positive");
        }
        if (maximumDigestLength < 0) {
            throw new IllegalArgumentException("maximumDigestLength must not be negative");
        }
        this.maximumCountBits = maximumCountBits;
        this.maximumCanonicalBytes = maximumCanonicalBytes;
        this.maximumKeysPerPayload = maximumKeysPerPayload;
        this.maximumEncodedPayloadBytes = maximumEncodedPayloadBytes;
        this.maximumIdentifierLength = maximumIdentifierLength;
        this.maximumDigestLength = maximumDigestLength;
    }

    public static ExactCountLimits defaults() {
        return new ExactCountLimits(
                DEFAULT_MAXIMUM_COUNT_BITS,
                DEFAULT_MAXIMUM_CANONICAL_BYTES,
                DEFAULT_MAXIMUM_KEYS_PER_PAYLOAD,
                DEFAULT_MAXIMUM_ENCODED_PAYLOAD_BYTES,
                DEFAULT_MAXIMUM_IDENTIFIER_LENGTH,
                DEFAULT_MAXIMUM_DIGEST_LENGTH);
    }

    public int maximumCountBits() {
        return maximumCountBits;
    }

    public int maximumCanonicalBytes() {
        return maximumCanonicalBytes;
    }

    public int maximumKeysPerPayload() {
        return maximumKeysPerPayload;
    }

    public int maximumEncodedPayloadBytes() {
        return maximumEncodedPayloadBytes;
    }

    public int maximumIdentifierLength() {
        return maximumIdentifierLength;
    }

    public int maximumDigestLength() {
        return maximumDigestLength;
    }

    public void validateNonNegative(BigInteger value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("exact counts must not be negative");
        }
        if (value.signum() != 0 && value.bitLength() > maximumCountBits) {
            throw new IllegalArgumentException(
                    "exact count exceeds maximum bit length " + maximumCountBits);
        }
    }

    public void validatePositive(BigInteger value) {
        validateNonNegative(value);
        if (value.signum() == 0) {
            throw new IllegalArgumentException("exact count must be positive");
        }
    }

    public void validateCanonicalBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0 || bytes.length > maximumCanonicalBytes) {
            throw new IllegalArgumentException("invalid canonical count byte length");
        }
        if (bytes.length > 1 && bytes[0] == 0) {
            throw new IllegalArgumentException("non-canonical count has a leading zero");
        }
        int bitLength = bytes.length == 1 && bytes[0] == 0
                ? 0
                : (bytes.length - 1) * 8 + (Integer.SIZE - Integer.numberOfLeadingZeros(bytes[0] & 0xff));
        if (bitLength > maximumCountBits) {
            throw new IllegalArgumentException(
                    "canonical count exceeds maximum bit length " + maximumCountBits);
        }
    }

    public void validateIdentifier(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        if (utf8Length(identifier) > maximumIdentifierLength) {
            throw new IllegalArgumentException("identifier exceeds maximum length");
        }
    }

    public void validateRequiredIdentifier(String identifier) {
        validateIdentifier(identifier);
        if (identifier.isEmpty()) {
            throw new IllegalArgumentException("identifier must not be empty");
        }
    }

    public void validateDigest(byte[] digest) {
        Objects.requireNonNull(digest, "digest");
        if (digest.length > maximumDigestLength) {
            throw new IllegalArgumentException("digest exceeds maximum length");
        }
    }

    public void validateKeyCount(int keyCount) {
        if (keyCount < 0 || keyCount > maximumKeysPerPayload) {
            throw new IllegalArgumentException("payload contains too many keys");
        }
    }

    public void validateEncodedPayloadLength(int length) {
        if (length < 0 || length > maximumEncodedPayloadBytes) {
            throw new IllegalArgumentException("encoded payload exceeds maximum length");
        }
    }

    public long toLongExact(BigInteger value) {
        validateNonNegative(value);
        return value.longValueExact();
    }

    public int toIntExact(BigInteger value) {
        validateNonNegative(value);
        return value.intValueExact();
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
