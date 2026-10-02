package com.dogpound.prideinventory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;
import zone.rong.mixinbooter.IEarlyMixinLoader;

/** Registers the one vanilla patch (extra slots in the player's inventory container). */
@IFMLLoadingPlugin.Name("PrideInventory")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1003)
public class PrideInventoryCore implements IFMLLoadingPlugin, IEarlyMixinLoader {
    private static final String CONFIG = "prideinventory.mixins.json";
    @Override public void injectData(Map<String, Object> data) { try { MixinBootstrap.init(); Mixins.addConfiguration(CONFIG); } catch (Throwable ignored) { } }
    @Override public List<String> getMixinConfigs() { return Collections.singletonList(CONFIG); }
    @Override public String[] getASMTransformerClass() { return new String[0]; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public String getAccessTransformerClass() { return null; }
}
