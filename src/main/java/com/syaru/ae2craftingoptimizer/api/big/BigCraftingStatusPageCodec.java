package com.syaru.ae2craftingoptimizer.api.big;

import com.syaru.ae2craftingoptimizer.engine.BigCraftingJob;
import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import com.syaru.ae2craftingoptimizer.engine.BigIntegerBufferCodec;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;

/** Strict status-page packet codec with protocol, entry, magnitude, and byte bounds. */
public final class BigCraftingStatusPageCodec<K> {
    public static final int PROTOCOL_VERSION = 1;
    public static final int HARD_MAXIMUM_PACKET_BYTES = 1_048_576;
    public static final int HARD_MAXIMUM_PAGE_ENTRIES = 16_384;

    private final BigCraftingPacketKeyCodec<K> keyCodec;
    private final int maximumBits;
    private final int maximumPageEntries;

    public BigCraftingStatusPageCodec(
            BigCraftingPacketKeyCodec<K> keyCodec,
            int maximumBits,
            int maximumPageEntries) {
        this.keyCodec = Objects.requireNonNull(keyCodec, "keyCodec");
        BigCountMath.requireMaximumBits(BigInteger.ZERO, "status maximum", maximumBits);
        // Status protocolは最低でも64bitの通常AE2量を表現できる設定だけを受け入れる。
        if (maximumBits < 64) {
            throw new IllegalArgumentException("maximumBits must be at least 64");
        }
        if (maximumPageEntries < 1 || maximumPageEntries > HARD_MAXIMUM_PAGE_ENTRIES) {
            throw new IllegalArgumentException("maximumPageEntries is outside the packet safety bound");
        }
        this.maximumBits = maximumBits;
        this.maximumPageEntries = maximumPageEntries;
    }

    public void write(FriendlyByteBuf buffer, BigCraftingStatusPage<K> page) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(page, "page");
        if (page.jobs().size() > maximumPageEntries) {
            throw new IllegalArgumentException("status page contains too many jobs");
        }
        int start = buffer.writerIndex();
        buffer.writeVarInt(PROTOCOL_VERSION);
        buffer.writeVarInt(maximumBits);
        buffer.writeUUID(page.runtimeId());
        buffer.writeVarInt(page.totalJobs());
        buffer.writeVarInt(page.offset());
        buffer.writeVarInt(page.jobs().size());
        BigIntegerBufferCodec.writeNonNegative(buffer, page.capacity(), maximumBits);
        BigIntegerBufferCodec.writeNonNegative(buffer, page.reserved(), maximumBits);
        BigIntegerBufferCodec.writeNonNegative(buffer, page.available(), maximumBits);
        ensurePacketBound(buffer.writerIndex() - start);
        for (BigCraftingStatusPage.JobSummary<K> job : page.jobs()) {
            buffer.writeUUID(job.id());
            keyCodec.write(buffer, job.requestedKey());
            BigIntegerBufferCodec.writeNonNegative(buffer, job.requestedAmount(), maximumBits);
            BigIntegerBufferCodec.writeNonNegative(buffer, job.reservedCapacity(), maximumBits);
            BigIntegerBufferCodec.writeNonNegative(buffer, job.remainingExecutions(), maximumBits);
            BigIntegerBufferCodec.writeNonNegative(buffer, job.waitingAmount(), maximumBits);
            buffer.writeVarInt(job.remainingTaskTypes());
            buffer.writeVarInt(job.waitingTypes());
            buffer.writeByte(job.state().ordinal());
            buffer.writeBoolean(job.executionPrepared());
            ensurePacketBound(buffer.writerIndex() - start);
        }
    }

    public BigCraftingStatusPage<K> read(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        if (buffer.readableBytes() > HARD_MAXIMUM_PACKET_BYTES) {
            throw new IllegalArgumentException("BigInteger status packet exceeds hard byte cap");
        }
        BigIntegerBufferCodec.requireProtocol(buffer.readVarInt());
        int remoteMaximumBits = buffer.readVarInt();
        if (remoteMaximumBits < 64 || remoteMaximumBits > maximumBits) {
            throw new IllegalStateException(
                    "ACO BigInteger magnitude mismatch: local " + maximumBits + ", remote " + remoteMaximumBits);
        }
        var runtimeId = buffer.readUUID();
        int totalJobs = readBoundedNonNegative(buffer, "totalJobs", Integer.MAX_VALUE);
        int offset = readBoundedNonNegative(buffer, "offset", totalJobs);
        int count = readBoundedNonNegative(buffer, "page entries", maximumPageEntries);
        var capacity = BigIntegerBufferCodec.readNonNegative(buffer, remoteMaximumBits);
        var reserved = BigIntegerBufferCodec.readNonNegative(buffer, remoteMaximumBits);
        var available = BigIntegerBufferCodec.readNonNegative(buffer, remoteMaximumBits);
        List<BigCraftingStatusPage.JobSummary<K>> jobs = new ArrayList<>(count);
        BigCraftingJob.State[] states = BigCraftingJob.State.values();
        for (int index = 0; index < count; index++) {
            var id = buffer.readUUID();
            K key = Objects.requireNonNull(keyCodec.read(buffer), "decoded status key");
            var requested = BigIntegerBufferCodec.readNonNegative(buffer, remoteMaximumBits);
            var reservation = BigIntegerBufferCodec.readNonNegative(buffer, remoteMaximumBits);
            var remaining = BigIntegerBufferCodec.readNonNegative(buffer, remoteMaximumBits);
            var waiting = BigIntegerBufferCodec.readNonNegative(buffer, remoteMaximumBits);
            int taskTypes = readBoundedNonNegative(buffer, "task types", 1_048_576);
            int waitingTypes = readBoundedNonNegative(buffer, "waiting types", 1_048_576);
            int stateOrdinal = buffer.readUnsignedByte();
            if (stateOrdinal >= states.length) {
                throw new IllegalArgumentException("invalid BigInteger job state " + stateOrdinal);
            }
            jobs.add(new BigCraftingStatusPage.JobSummary<>(
                    id,
                    key,
                    requested,
                    reservation,
                    remaining,
                    waiting,
                    taskTypes,
                    waitingTypes,
                    states[stateOrdinal],
                    buffer.readBoolean()));
        }
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("BigInteger status packet has trailing data");
        }
        return new BigCraftingStatusPage<>(runtimeId, capacity, reserved, available, totalJobs, offset, jobs);
    }

    private static int readBoundedNonNegative(FriendlyByteBuf buffer, String name, int maximum) {
        int value = buffer.readVarInt();
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(name + " is outside its packet bound");
        }
        return value;
    }

    private static void ensurePacketBound(int writtenBytes) {
        if (writtenBytes > HARD_MAXIMUM_PACKET_BYTES) {
            throw new PacketTooLargeException("BigInteger status packet exceeds hard byte cap");
        }
    }

    public static final class PacketTooLargeException extends IllegalArgumentException {
        public PacketTooLargeException(String message) {
            super(message);
        }
    }
}
