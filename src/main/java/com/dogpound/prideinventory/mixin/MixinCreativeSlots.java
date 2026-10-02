package com.dogpound.prideinventory.mixin;

import com.dogpound.prideinventory.Config;
import net.minecraft.network.NetHandlerPlayServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Creative mode saves inventory changes with one packet per slot, and vanilla only accepts slots 1-45. The extra
 * rows are slots 46+ of the same player container, so creative moves into them never reached the server (items
 * could vanish on relog). Accept our slots too — same checks, same code path.
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class MixinCreativeSlots {
    @ModifyConstant(method = "func_147344_a", constant = @Constant(intValue = 45), remap = false)   // processCreativeInventoryAction
    private int prideinv$extraSlots(int vanilla) {
        return vanilla + Config.MAX_SLOTS;
    }
}
