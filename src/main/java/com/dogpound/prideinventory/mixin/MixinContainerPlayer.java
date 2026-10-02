package com.dogpound.prideinventory.mixin;

import com.dogpound.prideinventory.Config;
import com.dogpound.prideinventory.ExtraInv;
import com.dogpound.prideinventory.ExtraSlot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The extra rows become real slots of the player's own inventory container, AFTER vanilla's 46 (so every mod that
 * counts on slot 0-45 keeps working). Same slots on client and server, so Minecraft syncs them like any other.
 */
@Mixin(ContainerPlayer.class)
public abstract class MixinContainerPlayer extends Container {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void prideinv$addRows(InventoryPlayer playerInventory, boolean localWorld, EntityPlayer player, CallbackInfo ci) {
        ExtraInv inv = ExtraInv.of(player);
        if (inv == null) return;
        for (int i = 0; i < Config.MAX_SLOTS; i++) addSlotToContainer(new ExtraSlot(inv, i));
    }

}
