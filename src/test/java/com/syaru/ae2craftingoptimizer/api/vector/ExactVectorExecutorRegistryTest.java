package com.syaru.ae2craftingoptimizer.api.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.networking.IGrid;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExactVectorExecutorRegistryTest {
    @AfterEach
    void clearRegistry() {
        ExactVectorExecutorRegistry.clear();
    }

    @Test
    void countsStandardAndBigParentReceiptsAcrossExecutors() {
        IGrid grid = testGrid();
        Object firstOwner = new Object();
        Object secondOwner = new Object();
        CountingExecutor first =
                new CountingExecutor("first", 2);
        CountingExecutor second =
                new CountingExecutor("second", 3);
        ExactVectorExecutorRegistry.register(
                grid,
                firstOwner,
                first);
        ExactVectorExecutorRegistry.register(
                grid,
                secondOwner,
                second);

        assertEquals(
                5,
                ExactVectorExecutorRegistry.activeTransactionCount(
                        grid));

        ExactVectorExecutorRegistry.unregister(grid, firstOwner);
        assertEquals(
                3,
                ExactVectorExecutorRegistry.activeTransactionCount(
                        grid));
    }

    @Test
    void skipsExecutorWhoseAvailabilityCheckFails() {
        IGrid grid = testGrid();
        ExactVectorExecutorRegistry.register(
                grid,
                new Object(),
                failingExecutor(true, false));

        assertTrue(ExactVectorExecutorRegistry.find(grid).isEmpty());
    }

    @Test
    void failsClosedWhenActiveCountCannotBeRead() {
        IGrid grid = testGrid();
        ExactVectorExecutorRegistry.register(
                grid,
                new Object(),
                failingExecutor(false, true));

        assertEquals(
                Integer.MAX_VALUE,
                ExactVectorExecutorRegistry.activeTransactionCount(grid));
    }

    @Test
    void failsClosedWhenExecutorReturnsNegativeActiveCount() {
        IGrid grid = testGrid();
        ExactVectorExecutorRegistry.register(
                grid,
                new Object(),
                new CountingExecutor("invalid", -1));

        assertEquals(
                Integer.MAX_VALUE,
                ExactVectorExecutorRegistry.activeTransactionCount(grid));
    }

    private static IGrid testGrid() {
        return (IGrid) Proxy.newProxyInstance(
                IGrid.class.getClassLoader(),
                new Class<?>[] {IGrid.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" ->
                            proxy == (arguments == null
                                    ? null
                                    : arguments[0]);
                    case "toString" -> "ACO test grid";
                    default -> throw new UnsupportedOperationException(
                            method.getName());
                });
    }

    private record CountingExecutor(String id, int active)
            implements ExactVectorExecutor {
        @Override
        public VectorExecutorIdentity identity() {
            return new VectorExecutorIdentity(id, id);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public int activeTransactionCount() {
            return active;
        }

        @Override
        public VectorExecutionOffer simulate(
                PreparedVectorBatch plan) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorStartResult start(
                PreparedVectorBatch plan) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorTransactionStatus status(
                UUID transactionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean cancel(UUID transactionId) {
            throw new UnsupportedOperationException();
        }
    }

    private static ExactVectorExecutor failingExecutor(
            boolean failAvailability,
            boolean failActiveCount) {
        return new ExactVectorExecutor() {
            @Override
            public VectorExecutorIdentity identity() {
                return new VectorExecutorIdentity("failing", "failing");
            }

            @Override
            public boolean isAvailable() {
                // 各テストはavailabilityと稼働数取得の故障を独立して再現する。
                if (failAvailability) {
                    throw new IllegalStateException("availability failure");
                }
                return true;
            }

            @Override
            public int activeTransactionCount() {
                // 稼働数取得の例外がGrid tickへ漏れないことを検証する。
                if (failActiveCount) {
                    throw new IllegalStateException("active count failure");
                }
                return 0;
            }

            @Override
            public VectorExecutionOffer simulate(PreparedVectorBatch plan) {
                throw new UnsupportedOperationException();
            }

            @Override
            public VectorStartResult start(PreparedVectorBatch plan) {
                throw new UnsupportedOperationException();
            }

            @Override
            public VectorTransactionStatus status(UUID transactionId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean cancel(UUID transactionId) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
