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
        TestPlots.addPlotClass(AcoCraftingPlots.class);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerTests);
    }

    private void registerTests(RegisterGameTestsEvent event) {
        event.register(Plots.class);
    }

    public static final class Plots {
        @GameTestGenerator
        public List<TestFunction> tests() {
            String scope = System.getProperty("aco.gametest.scope", "aco");
            if (!scope.equals("aco") && !scope.equals("all")) {
                throw new IllegalArgumentException("Unknown GameTest scope: " + scope);
            }
            var tests = new GameTestPlotAdapter().gameTestAdapter().stream()
                    .filter(t -> scope.equals("all") || t.getTestName().startsWith("ae2.aco_"))
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
