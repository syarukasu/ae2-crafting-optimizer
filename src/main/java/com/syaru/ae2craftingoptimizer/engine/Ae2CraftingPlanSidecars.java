package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftingPlan;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * BigInteger真値を純正AE2計画の外側へ保持するIdentity Sidecar。
 *
 * <p>AE2 15.4.xと周辺アドオンには公開型のICraftingPlanではなく、最終実装の
 * CraftingPlanへキャストする箇所がある。そのためACO独自計画を直接返さず、
 * AE2へは常に純正CraftingPlanを渡し、正確な値だけをこの表へ関連付ける。</p>
 */
public final class Ae2CraftingPlanSidecars {
    private static final ReferenceQueue<CraftingPlan> COLLECTED_FACADES =
            new ReferenceQueue<>();
    private static final Map<IdentityWeakReference, WideCraftingPlan> SIDECARS =
            new HashMap<>();

    private Ae2CraftingPlanSidecars() {
    }

    /**
     * ACO内部計画と同じlong互換表示を持つ純正AE2計画を作成する。
     *
     * <p>正確なBigInteger値、親Job、提出Claimはmetadata側にだけ残す。</p>
     */
    public static CraftingPlan expose(WideCraftingPlan metadata) {
        Objects.requireNonNull(metadata, "metadata");
        CraftingPlan facade = new CraftingPlan(
                metadata.finalOutput(),
                metadata.bytes(),
                metadata.simulation(),
                metadata.multiplePaths(),
                metadata.usedItems(),
                metadata.emittedItems(),
                metadata.missingItems(),
                metadata.patternTimes());
        synchronized (SIDECARS) {
            removeCollectedFacades();
            SIDECARS.put(
                    new IdentityWeakReference(facade, COLLECTED_FACADES),
                    metadata);
        }
        return facade;
    }

    /** 純正Facadeまたは旧内部呼出しのACO計画から、正確なSidecarを取得する。 */
    public static Optional<WideCraftingPlan> metadata(ICraftingPlan plan) {
        if (plan instanceof WideCraftingPlan widePlan) {
            return Optional.of(widePlan);
        }
        if (!(plan instanceof CraftingPlan facade)) {
            return Optional.empty();
        }
        synchronized (SIDECARS) {
            removeCollectedFacades();
            return Optional.ofNullable(SIDECARS.get(new IdentityWeakReference(facade)));
        }
    }

    /** 個別カウンタまでlongを超えた親計画だけを取得する。 */
    public static Optional<BigIntegerCraftingPlan> bigInteger(ICraftingPlan plan) {
        return metadata(plan)
                .filter(BigIntegerCraftingPlan.class::isInstance)
                .map(BigIntegerCraftingPlan.class::cast);
    }

    /** 個別カウンタはlong内で、CPU容量合計だけがlongを超えた計画を取得する。 */
    public static Optional<BigCapacityCraftingPlan> bigCapacity(ICraftingPlan plan) {
        return metadata(plan)
                .filter(BigCapacityCraftingPlan.class::isInstance)
                .map(BigCapacityCraftingPlan.class::cast);
    }

    /** 標準AE2 CPUへ渡してはいけないWide計画かを判定する。 */
    public static boolean isWide(ICraftingPlan plan) {
        return metadata(plan).isPresent();
    }

    /** 単体テスト間でstatic Sidecarを共有しないためのpackage-private reset。 */
    static void clearForTests() {
        synchronized (SIDECARS) {
            SIDECARS.clear();
            // 前テストで回収待ちになった参照も空になるまで取り除く。
            while (COLLECTED_FACADES.poll() != null) {
                // ReferenceQueueを空にすること自体が目的なので本体処理は不要。
            }
        }
    }

    private static void removeCollectedFacades() {
        IdentityWeakReference reference;
        // GC済みFacadeだけを除去し、画面や計算が保持中のPlanには触れない。
        while ((reference = (IdentityWeakReference) COLLECTED_FACADES.poll()) != null) {
            SIDECARS.remove(reference);
        }
    }

    /**
     * CraftingPlan recordのequalsではなく、同一インスタンスだけを一致させる弱参照。
     *
     * <p>内容が同じ二件の同時計算へ、別JobのSidecarを誤って返さないために必要。</p>
     */
    private static final class IdentityWeakReference extends WeakReference<CraftingPlan> {
        private final int identityHash;

        private IdentityWeakReference(
                CraftingPlan referent,
                ReferenceQueue<CraftingPlan> queue) {
            super(Objects.requireNonNull(referent, "referent"), queue);
            this.identityHash = System.identityHashCode(referent);
        }

        private IdentityWeakReference(CraftingPlan referent) {
            super(Objects.requireNonNull(referent, "referent"));
            this.identityHash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof IdentityWeakReference reference)) {
                return false;
            }
            CraftingPlan mine = get();
            // 回収済み参照同士を一致させると別Sidecarを消すため、nullは同一参照以外で一致させない。
            return mine != null && mine == reference.get();
        }
    }
}
