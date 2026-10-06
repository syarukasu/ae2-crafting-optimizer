package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.forgespi.language.IConfigurable;
import net.minecraftforge.forgespi.language.IModInfo;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MegaCellsModContextTest {
    @Test
    void foreignLanguageContextStillSelectsMegaCellsBus() throws Exception {
        var foreignExtension = new Object();
        var foreign = new Container("ae2megathings", foreignExtension);
        var bus = mock(IEventBus.class);
        var megaContext = javaContext(bus);
        var mega = new Container("megacells", megaContext);
        var modList = mock(ModList.class);
        doReturn(Optional.of(mega)).when(modList).getModContainerById("megacells");
        var active = ModLoadingContext.get();
        active.setActiveContainer(foreign);
        try (MockedStatic<ModList> registry = mockStatic(ModList.class)) {
            registry.when(ModList::get).thenReturn(modList);
            assertThrows(ClassCastException.class, FMLJavaModLoadingContext::get);
            var selected = MegaCellsModContext.get();
            assertSame(bus, selected.getModEventBus());
            assertSame(foreign, active.getActiveContainer());
            assertSame(foreignExtension, active.extension());
        } finally {
            active.setActiveContainer(null);
        }
    }

    @Test
    void foreignJavaContextDoesNotSilentlyRegisterOnAnotherModsBus() throws Exception {
        var foreignBus = mock(IEventBus.class);
        var foreignContext = javaContext(foreignBus);
        var foreign = new Container("another_java_mod", foreignContext);
        var megaBus = mock(IEventBus.class);
        var megaContext = javaContext(megaBus);
        var mega = new Container("megacells", megaContext);
        var modList = mock(ModList.class);
        doReturn(Optional.of(mega)).when(modList).getModContainerById("megacells");
        var active = ModLoadingContext.get();
        active.setActiveContainer(foreign);
        try (MockedStatic<ModList> registry = mockStatic(ModList.class)) {
            registry.when(ModList::get).thenReturn(modList);
            assertSame(foreignBus, FMLJavaModLoadingContext.get().getModEventBus());
            assertSame(megaBus, MegaCellsModContext.get().getModEventBus());
            assertSame(foreign, active.getActiveContainer());
            assertSame(foreignContext, active.extension());
        } finally {
            active.setActiveContainer(null);
        }
    }

    @Test
    void ownOrAbsentThreadContextSelectsSameOwner() throws Exception {
        var bus = mock(IEventBus.class);
        var context = javaContext(bus);
        var mega = new Container("megacells", context);
        var modList = mock(ModList.class);
        doReturn(Optional.of(mega)).when(modList).getModContainerById("megacells");
        var active = ModLoadingContext.get();
        try (MockedStatic<ModList> registry = mockStatic(ModList.class)) {
            registry.when(ModList::get).thenReturn(modList);
            active.setActiveContainer(null);
            assertSame(context, MegaCellsModContext.get());
            assertNull(active.extension());
            active.setActiveContainer(mega);
            assertSame(context, MegaCellsModContext.get());
            assertSame(mega, active.getActiveContainer());
            assertSame(context, active.extension());
        } finally {
            active.setActiveContainer(null);
        }
    }

    @Test
    void missingOwnerDoesNotFallBackToCallingMod() throws Exception {
        var foreignContext = javaContext(mock(IEventBus.class));
        var foreign = new Container("another_java_mod", foreignContext);
        var modList = mock(ModList.class);
        doReturn(Optional.empty()).when(modList).getModContainerById("megacells");
        var active = ModLoadingContext.get();
        active.setActiveContainer(foreign);
        try (MockedStatic<ModList> registry = mockStatic(ModList.class)) {
            registry.when(ModList::get).thenReturn(modList);
            var error = assertThrows(IllegalStateException.class, MegaCellsModContext::get);
            assertTrue(error.getMessage().contains("MEGACells"));
            assertSame(foreignContext, active.extension());
        } finally {
            active.setActiveContainer(null);
        }
    }

    private static FMLJavaModLoadingContext javaContext(IEventBus bus) throws Exception {
        var owner = mock(FMLModContainer.class);
        when(owner.getEventBus()).thenReturn(bus);
        var constructor = FMLJavaModLoadingContext.class.getDeclaredConstructor(FMLModContainer.class);
        constructor.setAccessible(true);
        return constructor.newInstance(owner);
    }

    private static final class Container extends ModContainer {
        Container(String id, Object extension) {
            super(info(id));
            contextExtension = () -> extension;
        }

        private static IModInfo info(String id) {
            var config = mock(IConfigurable.class);
            when(config.getConfigElement("displayTest")).thenReturn(Optional.of("NONE"));
            var info = mock(IModInfo.class);
            when(info.getModId()).thenReturn(id);
            when(info.getConfig()).thenReturn(config);
            return info;
        }

        @Override public boolean matches(Object value) { return false; }
        @Override public Object getMod() { return null; }
    }
}
