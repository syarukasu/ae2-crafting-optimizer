package com.syaru.ae2craftingoptimizer.api.contract;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Canonical unsigned-magnitude codec for non-negative exact quantities. */
public final class CanonicalBigIntegerCodec {
    public static final int NBT_SCHEMA_VERSION = 1;

    private CanonicalBigIntegerCodec() {
    }

    public static byte[] encodeNonNegative(BigInteger value, ExactCountLimits limits) {
        Objects.requireNonNull(limits, "limits").validateNonNegative(value);
        byte[] encoded = value.toByteArray();
        if (encoded.length > 1 && encoded[0] == 0) {
            encoded = Arrays.copyOfRange(encoded, 1, encoded.length);
        }
        limits.validateCanonicalBytes(encoded);
        return encoded;
    }

    public static byte[] encodePositive(BigInteger value, ExactCountLimits limits) {
        Objects.requireNonNull(limits, "limits").validatePositive(value);
        return encodeNonNegative(value, limits);
    }

    public static BigInteger decodeNonNegative(byte[] encoded, ExactCountLimits limits) {
        Objects.requireNonNull(limits, "limits").validateCanonicalBytes(encoded);
        return new BigInteger(1, encoded);
    }

    public static long toLongExact(BigInteger value, ExactCountLimits limits) {
        return Objects.requireNonNull(limits, "limits").toLongExact(value);
    }

    public static int toIntExact(BigInteger value, ExactCountLimits limits) {
        return Objects.requireNonNull(limits, "limits").toIntExact(value);
    }

    /**
     * Writes the schema and bytes beside the supplied key. A legacy string at the key itself is
     * removed so a subsequent save completes the migration to the canonical representation.
     */
    public static void writeNonNegative(
            CompoundTag tag,
            String key,
            BigInteger value,
            ExactCountLimits limits) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(key, "key");
        byte[] encoded = encodeNonNegative(value, limits);
        tag.putInt(schemaKey(key), NBT_SCHEMA_VERSION);
        tag.putByteArray(bytesKey(key), encoded);
        tag.remove(key);
    }

    /**
     * Reads the canonical form or the legacy decimal string form. Unknown schemas and mixed
     * canonical/legacy fields are rejected instead of being silently reset.
     */
    public static BigInteger readNonNegative(
            CompoundTag tag,
            String key,
            ExactCountLimits limits) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(key, "key");
        String schemaKey = schemaKey(key);
        String bytesKey = bytesKey(key);
        boolean hasSchema = tag.contains(schemaKey, Tag.TAG_INT);
        boolean hasBytes = tag.contains(bytesKey, Tag.TAG_BYTE_ARRAY);
        boolean hasLegacy = tag.contains(key, Tag.TAG_STRING);
        if (hasLegacy && (hasSchema || hasBytes)) {
            throw new IllegalArgumentException("mixed canonical and legacy count fields");
        }
        if (hasLegacy) {
            String legacy = tag.getString(key);
            if (legacy.isEmpty()) {
                throw new IllegalArgumentException("legacy count is empty");
            }
            try {
                BigInteger value = new BigInteger(legacy);
                limits.validateNonNegative(value);
                return value;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid legacy count", exception);
            }
        }
        if (!hasSchema || !hasBytes) {
            throw new IllegalArgumentException("missing canonical count fields");
        }
        if (tag.getInt(schemaKey) != NBT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unknown exact count NBT schema");
        }
        return decodeNonNegative(tag.getByteArray(bytesKey), limits);
    }

    private static String schemaKey(String key) {
        return key + "_schema";
    }

    private static String bytesKey(String key) {
        return key + "_bytes";
    }
}
