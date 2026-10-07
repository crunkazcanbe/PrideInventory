package com.dogpound.prideinventory;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client half of Net.Sync — kept out of Net so the dedicated server never touches client classes. */
@SideOnly(Side.CLIENT)
final class ClientSync {
    private ClientSync() {}

    static void apply(Net.Sync m) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            ExtraInv i = ExtraInv.of(Minecraft.getMinecraft().player);
            if (i == null) return;
            i.unlocked = m.unlocked; i.page = m.page; i.advancements = m.advancements; i.hours = m.hours;
            i.blocksMined = m.blocksMined; i.mobKills = m.mobKills; i.bought = m.bought; i.granted = m.granted;
            ClientHooks.nextPrice = m.nextPrice;
            ClientHooks.everything = m.everything;
            i.shareOpen = m.shareOpen; i.giveWhenLocked = m.giveWhenLocked;
        });
    }
}
