package com.dogpound.prideinventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

/**
 * Her ask 2026-10-02: the hotbar runs along the whole bottom of the screen, edge to edge, and shows only what's
 * UNLOCKED — page 1 (the real 9 slots) then every unlocked row, left to right. The scroll wheel walks along all of it;
 * stepping into another row asks the server to swap that row into the real hotbar (Pages.goTo), so every other mod
 * still sees a normal 9-slot hotbar and nothing can be duplicated. Number keys still pick within the current page.
 */
final class WideHotbar extends Gui {
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    private static final int PAGE_W = 182, GAP = 4;

    private static ExtraInv inv() { return ExtraInv.of(Minecraft.getMinecraft().player); }

    /** pages shown: the real hotbar + every unlocked row that can be a page */
    private static int groups(ExtraInv inv) { return inv == null ? 1 : Pages.pageRows(inv.unlocked) + 1; }

    /** the item at logical page L, column c — pages rotate through the real hotbar, so find where page L lives now */
    private static ItemStack at(EntityPlayer p, ExtraInv inv, int pages, int L, int c) {
        int i = Math.floorMod(L - inv.page, pages);
        return i == 0 ? p.inventory.mainInventory.get(c) : inv.items.getStackInSlot((i - 1) * 9 + c);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void draw(RenderGameOverlayEvent.Pre e) {
        if (!Config.wideHotbar || e.getType() != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.player;
        ExtraInv inv = inv();
        if (p == null || inv == null || p.isSpectator()) return;
        e.setCanceled(true);                                         // we draw the hotbar ourselves

        ScaledResolution r = e.getResolution();
        int w = r.getScaledWidth(), h = r.getScaledHeight(), n = groups(inv);
        int total = n * PAGE_W + (n - 1) * GAP;
        int selX = inv.page * (PAGE_W + GAP) + 3 + p.inventory.currentItem * 20;    // selected slot, from the bar's start
        int x0 = total <= w - 4 ? (w - total) / 2 : Math.max(w - total - 2, Math.min(2, w / 2 - selX));
        int y = h - 22;

        drawRect(0, y - 1, w, h, 0x99000000);                         // the band: edge to edge
        drawRect(0, y - 1, w, y, 0xFFB46CFF);                         // thin Pride accent line on top
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
        mc.getTextureManager().bindTexture(WIDGETS);
        for (int g = 0; g < n; g++) {
            int gx = x0 + g * (PAGE_W + GAP);
            if (gx + PAGE_W < 0 || gx > w) continue;
            drawTexturedModalRect(gx, y, 0, 0, PAGE_W, 22);              // a real hotbar strip per page
        }
        drawTexturedModalRect(x0 + selX - 4, y - 1, 0, 22, 24, 24);     // selection frame
        // offhand slot, where vanilla puts it (left of the bar) — or above the bar's left end when the bar is full width
        ItemStack off = p.getHeldItemOffhand();
        int ox = x0 >= 31 ? x0 - 29 : 2, oy = x0 >= 31 ? y - 1 : y - 26;
        if (!off.isEmpty()) drawTexturedModalRect(ox, oy, 24, 22, 29, 24);

        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableRescaleNormal();
        for (int g = 0; g < n; g++) {
            int gx = x0 + g * (PAGE_W + GAP);
            if (gx + PAGE_W < 0 || gx > w) continue;
            for (int c = 0; c < 9; c++) {
                ItemStack s = at(p, inv, n, g, c);
                if (s.isEmpty()) continue;
                int ix = gx + 3 + c * 20, iy = y + 3;
                mc.getRenderItem().renderItemAndEffectIntoGUI(p, s, ix, iy);
                mc.getRenderItem().renderItemOverlays(mc.fontRenderer, s, ix, iy);
            }
            if (n > 1) mc.fontRenderer.drawStringWithShadow(String.valueOf(g + 1), gx + 1, y - 9, g == inv.page ? 0xFFFFFF55 : 0xFFAAAAAA);
        }
        if (!off.isEmpty()) {
            mc.getRenderItem().renderItemAndEffectIntoGUI(p, off, ox + 3, oy + 4);
            mc.getRenderItem().renderItemOverlays(mc.fontRenderer, off, ox + 3, oy + 4);
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
    }

    /** the scroll wheel walks along the whole bar (held Shift/Ctrl/Alt = leave the wheel to other mods) */
    @SubscribeEvent
    public void wheel(MouseEvent e) {
        if (!Config.wideHotbar || e.getDwheel() == 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null || mc.player.isSpectator()) return;
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_LMENU)) return;
        ExtraInv inv = inv();
        int n = groups(inv);
        if (inv == null || n <= 1) return;                           // one page: vanilla's own scrolling is the same
        e.setCanceled(true);
        int total = n * 9, step = e.getDwheel() > 0 ? -1 : 1;
        int g = Math.floorMod(inv.page * 9 + mc.player.inventory.currentItem + step, total);
        int page = g / 9;
        mc.player.inventory.currentItem = g % 9;
        if (page != inv.page) {
            Net.CH.sendToServer(new Net.GoTo(page));
            inv.page = page;                                         // move the frame now; the server's sync confirms it
        }
    }
}
