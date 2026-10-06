package com.syaru.ae2craftingoptimizer.gametest;

import appeng.server.testplots.TestPlots;
import appeng.server.testworld.GameTestPlotAdapter;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("aco_gametest")
public final class AcoGameTestMod {
    public AcoGameTestMod() {
        String report = System.getProperty("aco.gametest.report");
        if (report == null) throw new IllegalStateException("Missing runtime report destination");
        try {
            GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(new File(report)));
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Cannot initialize runtime report", e);
        }
        if (!Boolean.getBoolean("aco.gametest.baseline")) {
            TestPlots.addPlotClass(AcoCraftingPlots.class);
            TestPlots.addPlotClass(ProviderDispatchPlots.class);
            if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism"))
                TestPlots.addPlotClass(MekanismIntentPlots.class);
            if (net.minecraftforge.fml.ModList.get().isLoaded("gtceu"))
                TestPlots.addPlotClass(GTIntentPlots.class);
        }
        if (List.of("historical", "providers").contains(System.getProperty("aco.gametest.scope", "aco"))) {
            try {
                TestPlots.addPlotClass(Class.forName("jp.main.taikun.insaneae.testplots.InsaneAETestPlots"));
                if (!Boolean.getBoolean("aco.gametest.baseline")) TestPlots.addPlotClass(AddOnPlanningPlots.class);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Historical regression requires the pinned InsaneAE diagnostic JAR", e);
            }
        }
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerTests);
    }

    private void registerTests(RegisterGameTestsEvent event) {
        event.register(Plots.class);
    }

    public static final class Plots {
        @GameTestGenerator
        public List<TestFunction> tests() {
            String scope = System.getProperty("aco.gametest.scope", "aco");
            if (!List.of("aco", "all", "historical", "providers", "machines").contains(scope)) {
                throw new IllegalArgumentException("Unknown GameTest scope: " + scope);
            }
            var tests = new GameTestPlotAdapter().gameTestAdapter().stream()
                    .filter(t -> scope.equals("machines") ? List.of("ae2.aco_gt_intent_completion",
                            "ae2.aco_mekanism_intent_completion").contains(t.getTestName())
                            : scope.equals("all") || (scope.equals("historical")
                            ? List.of("ae2.pattern_provider_faces_round_robin", "ae2.insaneae_pattern_provider_patterns",
                                    "ae2.insaneae_crafting_batch", "ae2.insaneae_crafting_batch_chain")
                                    .contains(t.getTestName())
                            : t.getTestName().startsWith("ae2.aco_") || (scope.equals("providers")
                                    && List.of("ae2.pattern_provider_faces_round_robin", "ae2.insaneae_pattern_provider_patterns")
                                            .contains(t.getTestName()))))
                    .toList();
            try {
                Files.writeString(Path.of(System.getProperty("aco.gametest.report") + ".expected.json"),
                        new Gson().toJson(tests.stream().map(TestFunction::getTestName).toList()));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot record expected runtime tests", e);
            }
            return tests;
        }
    }
}
