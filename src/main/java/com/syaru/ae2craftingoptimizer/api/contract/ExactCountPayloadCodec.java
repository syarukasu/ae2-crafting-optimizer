package com.syaru.ae2craftingoptimizer.api.contract;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Deterministic binary/NBT codec shared by every exact-count payload owner. */
public final class ExactCountPayloadCodec {
    public static final int NBT_SCHEMA_VERSION = 1;
    private static final int MAGIC = 0x41434f31;

    private ExactCountPayloadCodec() {
    }

    public static byte[] encode(ExactCountPayload payload, ExactCountLimits limits) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(limits, "limits");
        // Revalidate at the codec boundary so callers cannot pair a payload with a narrower store.
        ExactCountPayload validated = ExactCountPayload.of(
                payload.kind(), payload.identifier(), payload.counts(), payload.digest(), limits);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeByte(NBT_SCHEMA_VERSION);
            output.writeByte(validated.kind().wireId());
            writeString(output, validated.identifier(), limits, false);
            byte[] digest = validated.digest();
            limits.validateDigest(digest);
            output.writeInt(digest.length);
            output.write(digest);
            limits.validateKeyCount(validated.counts().size());
            output.writeInt(validated.counts().size());
            for (Map.Entry<String, BigInteger> entry : validated.counts().entrySet()) {
                writeString(output, entry.getKey(), limits, true);
                byte[] count = CanonicalBigIntegerCodec.encodeNonNegative(entry.getValue(), limits);
                output.writeInt(count.length);
                output.write(count);
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            limits.validateEncodedPayloadLength(encoded.length);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory exact-count encoding failed", exception);
        }
    }

    public static ExactCountPayload decode(byte[] encoded, ExactCountLimits limits) {
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(limits, "limits");
        limits.validateEncodedPayloadLength(encoded.length);
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("invalid exact-count payload magic");
            }
            if (input.readUnsignedByte() != NBT_SCHEMA_VERSION) {
                throw new IllegalArgumentException("unknown exact-count payload schema");
            }
            PayloadKind kind = PayloadKind.fromWireId(input.readUnsignedByte());
            String identifier = readString(input, limits, false);
            int digestLength = readLength(input, limits.maximumDigestLength(), "digest");
            byte[] digest = input.readNBytes(digestLength);
            if (digest.length != digestLength) {
                throw new EOFException("truncated exact-count digest");
            }
            int keyCount = readLength(input, limits.maximumKeysPerPayload(), "key");
            java.util.Map<String, BigInteger> counts = new java.util.TreeMap<>();
            String previousKey = null;
            for (int index = 0; index < keyCount; index++) {
                String key = readString(input, limits, true);
                if (previousKey != null && previousKey.compareTo(key) >= 0) {
                    throw new IllegalArgumentException("exact-count keys are not canonical");
                }
                previousKey = key;
                int countLength = readLength(input, limits.maximumCanonicalBytes(), "count");
                byte[] countBytes = input.readNBytes(countLength);
                if (countBytes.length != countLength) {
                    throw new EOFException("truncated exact-count value");
                }
                BigInteger count = CanonicalBigIntegerCodec.decodeNonNegative(countBytes, limits);
                if (counts.put(key, count) != null) {
                    throw new IllegalArgumentException("duplicate exact-count key");
                }
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException("trailing bytes in exact-count payload");
            }
            ExactCountPayload payload = ExactCountPayload.of(kind, identifier, counts, digest, limits);
            if (!Arrays.equals(encoded, encode(payload, limits))) {
                throw new IllegalArgumentException("non-canonical exact-count payload");
            }
            return payload;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("truncated exact-count payload", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("invalid exact-count payload", exception);
        }
    }

    public static void writeToNbt(
            CompoundTag tag,
            String key,
            ExactCountPayload payload,
            ExactCountLimits limits) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(key, "key");
        tag.putInt(schemaKey(key), NBT_SCHEMA_VERSION);
        tag.putByteArray(bytesKey(key), encode(payload, limits));
        tag.remove(key);
    }

    public static ExactCountPayload readFromNbt(
            CompoundTag tag,
            String key,
            ExactCountLimits limits) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(key, "key");
        String schemaKey = schemaKey(key);
        String bytesKey = bytesKey(key);
        if (tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("legacy payload has no safe migration");
        }
        if (!tag.contains(schemaKey, Tag.TAG_INT) || !tag.contains(bytesKey, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalArgumentException("missing exact-count payload fields");
        }
        if (tag.getInt(schemaKey) != NBT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unknown exact-count payload NBT schema");
        }
        return decode(tag.getByteArray(bytesKey), limits);
    }

    private static int readLength(DataInputStream input, int maximum, String label) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximum) {
            throw new IllegalArgumentException(label + " length exceeds the exact-count limit");
        }
        return length;
    }

    private static void writeString(
            DataOutputStream output,
            String value,
            ExactCountLimits limits,
            boolean required) throws IOException {
        if (required) {
            limits.validateRequiredIdentifier(value);
        } else {
            limits.validateIdentifier(value);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(
            DataInputStream input,
            ExactCountLimits limits,
            boolean required) throws IOException {
        int length = readLength(input, limits.maximumIdentifierLength(), "identifier");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("truncated identifier");
        }
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (required) {
                limits.validateRequiredIdentifier(value);
            } else {
                limits.validateIdentifier(value);
            }
            return value;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("identifier is not valid UTF-8", exception);
        }
    }

    private static String schemaKey(String key) {
        return key + "_schema";
    }

    private static String bytesKey(String key) {
        return key + "_bytes";
    }
}
