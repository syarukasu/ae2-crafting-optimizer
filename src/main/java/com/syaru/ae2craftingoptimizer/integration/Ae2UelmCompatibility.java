package com.syaru.ae2craftingoptimizer.integration;

import java.util.Set;
import net.minecraftforge.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/**
 * Evidence-based compatibility boundary for AE2 Unofficial Extended Life Modern.
 *
 * <p>UELM is a replacement AE2 distribution: it keeps the {@code ae2} mod id
 * and identifies its Forge 1.20.1 build as {@code 15.5.0-uelm}. ACO therefore
 * detects the loaded AE2 version instead of looking for a second mod id.</p>
 */
public final class Ae2UelmCompatibility {
    public static final String UPSTREAM_VERSION = "15.4.10";
    public static final String UPSTREAM_PATCH_VERSION = "15.4.11";
    public static final String UELM_VERSION = "15.5.0-uelm";
    private static final DefaultArtifactVersion MINIMUM_VERSION = new DefaultArtifactVersion(UPSTREAM_VERSION);

    /** Only the three int-based craft amount Mixins overlap with UELM. */
    private static final Set<String> UELM_OWNED_MIXINS = Set.of(
            "CraftAmountMenuLongAmountMixin",
            "CraftConfirmMenuLongAmountMixin",
            "CraftAmountScreenLongAmountMixin");

    private Ae2UelmCompatibility() {
    }

    /** Pure version predicate used by runtime detection and unit tests. */
    public static boolean isUelmVersion(String version) {
        return UELM_VERSION.equals(version);
    }

    /** Issue #225: version admission has a floor, not a whitelist or ceiling. */
    public static boolean isSupportedAe2Version(String version) {
        return version != null && !version.isBlank()
                && new DefaultArtifactVersion(version).compareTo(MINIMUM_VERSION) >= 0;
    }

    /** Returns the version of the shared {@code ae2} mod id, if it is loaded. */
    public static String loadedAe2Version() {
        return ModList.get().getModContainerById("ae2")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }

    /** Returns true only for the published UELM version/profile. */
    public static boolean isLoaded() {
        return isUelmVersion(loadedAe2Version());
    }

    /** Returns whether the named ACO Mixin overlaps UELM's verified long surface. */
    public static boolean ownsAe2SurfaceMixin(String mixinClassName) {
        int separator = mixinClassName.lastIndexOf('.');
        String simpleName = separator >= 0
                ? mixinClassName.substring(separator + 1)
                : mixinClassName;
        return UELM_OWNED_MIXINS.contains(simpleName);
    }

    /** ACO's legacy int-based root amount path is delegated to UELM. */
    public static boolean ownsAe2ExtendedAmountSurface() {
        return isLoaded();
    }

    /** UELM did not change the verified NetworkStorage/KeyCounter surface. */
    public static boolean ownsAe2StorageSurface() {
        return false;
    }
}
