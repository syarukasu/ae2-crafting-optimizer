package com.syaru.ae2craftingoptimizer.registry;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.menu.BigCraftingStatusMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** ACOが所有するContainer Menuの登録点。 */
public final class ACOMenus {
    private static final DeferredRegister<MenuType<?>> TYPES =
            DeferredRegister.create(
                    ForgeRegistries.MENU_TYPES,
                    AE2CraftingOptimizer.MODID);

    public static final RegistryObject<MenuType<BigCraftingStatusMenu>>
            BIG_CRAFTING_STATUS = TYPES.register(
                    "big_crafting_status",
                    () -> IForgeMenuType.create(
                            BigCraftingStatusMenu::new));

    private ACOMenus() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
