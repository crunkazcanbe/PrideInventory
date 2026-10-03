package com.dogpound.prideinventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** The extra-rows panel (scrolls, shows what unlocks locked rows), ◀ ▶ page arrows by the hotbar, keys and the HUD. */
public final class ClientHooks {
    static long nextPrice;
    static boolean everything;
    private static int scroll;
    private static boolean dragging;
    private static final int ID_PREV = 0x50494E01, ID_NEXT = 0x50494E02, ID_BUY = 0x50494E03;
    private static final int PREV_X = 107, NEXT_X = 141, BUY_X = 157;   // footer buttons, from the panel's left: ◀ 1/7 ▶ +
    private static final KeyBinding NEXT = new KeyBinding("Next hotbar page", Keyboard.KEY_APOSTROPHE, "PrideInventory");
    private static final KeyBinding PREV = new KeyBinding("Previous hotbar page", Keyboard.KEY_NONE, "PrideInventory");
    private static final KeyBinding JUMP = new KeyBinding("Jump to hotbar slot # (type 1-100)", Keyboard.KEY_SEMICOLON, "PrideInventory");

    static void register() {
        ClientRegistry.registerKeyBinding(NEXT);
        ClientRegistry.registerKeyBinding(PREV);
        ClientRegistry.registerKeyBinding(JUMP);
        MinecraftForge.EVENT_BUS.register(new ClientHooks());
        MinecraftForge.EVENT_BUS.register(new WideHotbar());
    }

    private static ExtraInv inv() {
        return ExtraInv.of(Minecraft.getMinecraft().player);
    }

    static int pages(ExtraInv inv) {
        return Pages.pageRows(inv.unlocked) + 1;
    }

    // ---------------- keys ----------------
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || Minecraft.getMinecraft().player == null) return;
        while (NEXT.isPressed()) Net.CH.sendToServer(new Net.Flip(1));
        while (PREV.isPressed()) Net.CH.sendToServer(new Net.Flip(-1));
        while (JUMP.isPressed()) if (Config.wideHotbar && Minecraft.getMinecraft().currentScreen == null) Minecraft.getMinecraft().displayGuiScreen(new GuiSlotNumber());
    }

    // ---------------- panel geometry (relative to the inventory's top-left) ----------------
    private static boolean right() { return "right".equals(Config.panelSide); }
    private static int panelX(GuiContainer g) { return right() ? g.getXSize() + 4 : (g.getXSize() - 176) / 2; }
    private static int panelH() { return 14 + visible() * 18 + 12; }   // rows + the footer strip (◀ 1/7 ▶ +)
    private static int panelY(GuiContainer g) {
        if (right()) return 0;
        int y = g.getYSize() + (creative(g) ? 30 : 2);                  // creative: below its bottom tab row
        return Math.min(y, g.height - g.getGuiTop() - panelH());       // never off the bottom of the screen (small windows)
    }

    private static boolean creative(net.minecraft.client.gui.GuiScreen g) { return g instanceof net.minecraft.client.gui.inventory.GuiContainerCreative; }

    /** her ask 2026-09-28: the panel in the survival inventory AND in creative's inventory tab, under the whole inventory */
    static boolean shows(net.minecraft.client.gui.GuiScreen g) {
        if (Config.maxRows == 0) return false;
        if (g instanceof GuiInventory) return true;
        if (!creative(g)) return false;
        try {
            int tab = net.minecraftforge.fml.common.ObfuscationReflectionHelper.getPrivateValue(net.minecraft.client.gui.inventory.GuiContainerCreative.class, null, "field_147058_w");
            return tab == net.minecraft.creativetab.CreativeTabs.INVENTORY.getTabIndex();
        } catch (Throwable t) { return false; }
    }

    /** creative wraps every slot in its own CreativeSlot — reach through to ours */
    private static ExtraSlot extra(Slot s) {
        if (s instanceof ExtraSlot) return (ExtraSlot) s;
        if (!s.getClass().getName().endsWith("CreativeSlot")) return null;
        try {
            @SuppressWarnings("unchecked") Object in = net.minecraftforge.fml.common.ObfuscationReflectionHelper.getPrivateValue((Class<Object>) creativeSlotClass(), s, "field_148332_b");
            return in instanceof ExtraSlot ? (ExtraSlot) in : null;
        } catch (Throwable t) { return null; }
    }

    private static Class creativeSlotClass() {
        for (Class<?> c : net.minecraft.client.gui.inventory.GuiContainerCreative.class.getDeclaredClasses()) if (c.getSimpleName().equals("CreativeSlot")) return c;
        return Object.class;
    }
    private static int visible() { return Math.max(1, Math.min(Config.visibleRows, Math.max(1, Config.maxRows))); }

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post e) {
        if (!(e.getGui() instanceof GuiInventory) || Config.maxRows == 0) return;
        GuiContainer g = (GuiContainer) e.getGui();
        // all inside the panel's bottom strip, right end: ◀ 1/7 ▶ +  (by the hotbar they sat on the side dock)
        int px = g.getGuiLeft() + panelX(g), fy = g.getGuiTop() + panelY(g) + 6 + visible() * 18 + 2;
        if (Config.hotbarPages > 0) {
            e.getButtonList().add(new Arrow(ID_PREV, px + PREV_X, fy, "◀"));
            e.getButtonList().add(new Arrow(ID_NEXT, px + NEXT_X, fy, "▶"));
        }
        if (Config.buyWithCoins)
            e.getButtonList().add(new Arrow(ID_BUY, px + BUY_X, fy, "+"));
        clamp();
    }

    @SubscribeEvent
    public void onButton(GuiScreenEvent.ActionPerformedEvent.Pre e) {
        int id = e.getButton().id;
        if (id == ID_PREV) { Net.CH.sendToServer(new Net.Flip(-1)); e.setCanceled(true); }
        else if (id == ID_NEXT) { Net.CH.sendToServer(new Net.Flip(1)); e.setCanceled(true); }
        else if (id == ID_BUY) { Net.CH.sendToServer(new Net.Buy()); e.setCanceled(true); }
    }

    private static void clamp() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, Config.maxRows - visible())));
    }

    /** put the visible rows' slots on screen, hide the rest (hidden slots can't be clicked or drawn) */
    private static void place(GuiContainer g) {
        int px = panelX(g) + 8, py = panelY(g) + 7;
        for (Slot s : g.inventorySlots.inventorySlots) {
            ExtraSlot es = extra(s);
            if (es == null) continue;
            int row = es.index / 9, col = es.index % 9;
            boolean shown = row < Config.maxRows && row >= scroll && row < scroll + visible();
            s.xPos = es.xPos = shown ? px + col * 18 : ExtraSlot.HIDDEN;     // creative: the wrapper AND ours
            s.yPos = es.yPos = shown ? py + (row - scroll) * 18 : ExtraSlot.HIDDEN;
        }
    }

    /** creative, another tab: any of our slots still in the container stay off-screen */
    private static void hideInCreative(GuiContainer g) {
        for (Slot s : g.inventorySlots.inventorySlots) { ExtraSlot es = extra(s); if (es != null) { s.xPos = s.yPos = ExtraSlot.HIDDEN; } }
    }

    /** panel background, drawn before the slots */
    @SubscribeEvent
    public void onBackground(GuiScreenEvent.BackgroundDrawnEvent e) {
        if (!shows(e.getGui())) { if (creative(e.getGui())) hideInCreative((GuiContainer) e.getGui()); return; }
        GuiContainer g = (GuiContainer) e.getGui();
        place(g);
        int x = g.getGuiLeft() + panelX(g), y = g.getGuiTop() + panelY(g), w = 176, h = panelH();
        Gui.drawRect(x, y, x + w, y + h, 0xFFC6C6C6);                         // vanilla inventory grey, with its bevel
        Gui.drawRect(x, y, x + w, y + 1, 0xFFFFFFFF); Gui.drawRect(x, y, x + 1, y + h, 0xFFFFFFFF);
        Gui.drawRect(x + w - 1, y, x + w, y + h, 0xFF555555); Gui.drawRect(x, y + h - 1, x + w, y + h, 0xFF555555);
        for (int r = 0; r < visible(); r++)
            for (int c = 0; c < 9; c++) {
                int sx = x + 7 + c * 18, sy = y + 6 + r * 18;
                Gui.drawRect(sx, sy, sx + 18, sy + 18, 0xFF8B8B8B);
                Gui.drawRect(sx, sy, sx + 17, sy + 1, 0xFF373737); Gui.drawRect(sx, sy, sx + 1, sy + 17, 0xFF373737);
                Gui.drawRect(sx + 17, sy, sx + 18, sy + 18, 0xFFFFFFFF); Gui.drawRect(sx, sy + 17, sx + 18, sy + 18, 0xFFFFFFFF);
            }
    }

    /** locked rows, the scroll bar, labels and tooltips, drawn on top */
    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (!shows(e.getGui())) return;
        GuiContainer g = (GuiContainer) e.getGui();
        ExtraInv inv = inv();
        if (inv == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        int x = g.getGuiLeft() + panelX(g), y = g.getGuiTop() + panelY(g);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        String lockTip = null;
        for (int r = 0; r < visible(); r++) {
            int row = scroll + r;
            if (row >= Config.maxRows || row < inv.unlocked) continue;
            int rx = x + 7, ry = y + 6 + r * 18;
            Gui.drawRect(rx, ry, rx + 162, ry + 18, 0xB0101018);
            mc.fontRenderer.drawStringWithShadow("🔒 Row " + (row + 1) + " locked", rx + 6, ry + 5, 0xFFB0B0B0);
            if (e.getMouseX() >= rx && e.getMouseX() < rx + 162 && e.getMouseY() >= ry && e.getMouseY() < ry + 18) lockTip = howToUnlock(inv);
        }
        // scroll bar on the right
        int track = visible() * 18, bx = x + 176 - 6, by = y + 6;
        if (Config.maxRows > visible()) {
            Gui.drawRect(bx, by, bx + 4, by + track, 0xFF555555);
            int knob = Math.max(8, track * visible() / Config.maxRows);
            int ky = by + (track - knob) * scroll / Math.max(1, Config.maxRows - visible());
            Gui.drawRect(bx, ky, bx + 4, ky + knob, 0xFFF5A9B8);
        }
        String head = inv.unlocked + "/" + Config.maxRows + " rows" + (everything ? " §2(all)" : "");
        mc.fontRenderer.drawString(head, x + 7, y + 6 + track + 4, 0xFF404040);
        if (Config.hotbarPages > 0) {                               // "1/7" between the ◀ ▶ footer arrows
            String pg = (inv.page + 1) + "/" + pages(inv);
            mc.fontRenderer.drawString(pg, x + PREV_X + 12 + (NEXT_X - PREV_X - 12 - mc.fontRenderer.getStringWidth(pg)) / 2, y + 6 + track + 4, 0xFF404040);
        }
        GlStateManager.enableDepth();
        if (lockTip != null) g.drawHoveringText(splitTip(lockTip), e.getMouseX(), e.getMouseY());
    }

    private static List<String> splitTip(String s) {
        List<String> out = new ArrayList<>();
        for (String line : s.split("\n")) out.add(line);
        return out;
    }

    /** the tooltip on a locked row: every way to get the next one */
    static String howToUnlock(ExtraInv inv) {
        StringBuilder sb = new StringBuilder("§dUnlock the next row by any of:");
        if (Config.advancementsPerRow > 0) sb.append("\n§7• ").append(need(inv.advancements, Config.advancementsPerRow)).append(" more advancements");
        if (Config.hoursPerRow > 0) sb.append("\n§7• ").append(need(inv.hours, Config.hoursPerRow)).append(" more hours played");
        if (Config.blocksMinedPerRow > 0) sb.append("\n§7• ").append(need(inv.blocksMined, Config.blocksMinedPerRow)).append(" more blocks mined");
        if (Config.mobKillsPerRow > 0) sb.append("\n§7• ").append(need(inv.mobKills, Config.mobKillsPerRow)).append(" more mobs killed");
        if (Config.buyWithCoins) sb.append("\n§7• buy it for §6").append(nextPrice).append(" coins §7(the + button)");
        sb.append("\n§7• quest rewards");
        return sb.toString();
    }

    private static int need(int have, int per) {
        return per - have % per;
    }

    /** mouse wheel over the panel scrolls it; dragging the bar scrolls it */
    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre e) {
        if (!shows(e.getGui()) || Config.maxRows <= visible()) return;
        GuiContainer g = (GuiContainer) e.getGui();
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int mx = Mouse.getEventX() * sr.getScaledWidth() / Minecraft.getMinecraft().displayWidth;
        int my = sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / Minecraft.getMinecraft().displayHeight - 1;
        int x = g.getGuiLeft() + panelX(g), y = g.getGuiTop() + panelY(g), h = panelH();
        boolean over = mx >= x && mx < x + 176 && my >= y && my < y + h;
        int d = Mouse.getEventDWheel();
        if (over && d != 0) { scroll += d > 0 ? -1 : 1; clamp(); e.setCanceled(true); return; }
        int bx = x + 170;
        if (Mouse.getEventButton() == 0) dragging = Mouse.getEventButtonState() && mx >= bx && mx < bx + 6 && my >= y + 6 && my < y + 6 + visible() * 18;
        if (dragging) {
            scroll = (my - y - 6) * (Config.maxRows - visible() + 1) / Math.max(1, visible() * 18);
            clamp();
            e.setCanceled(true);
        }
    }

    /** "◀ 2/5 ▶" beside the hotbar */
    @SubscribeEvent
    public void onHud(RenderGameOverlayEvent.Post e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.HOTBAR || !Config.hudIndicator || Config.hotbarPages == 0 || Config.wideHotbar) return;
        ExtraInv inv = inv();
        if (inv == null || pages(inv) <= 1) return;
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = e.getResolution();
        String s = "◀ " + (inv.page + 1) + "/" + pages(inv) + " ▶";
        int x = sr.getScaledWidth() / 2 - 91 - 6 - mc.fontRenderer.getStringWidth(s), y = sr.getScaledHeight() - 15;
        mc.fontRenderer.drawStringWithShadow(s, x, y, 0xFFF5A9B8);
    }

    /** small square button (arrows, +) */
    static final class Arrow extends GuiButton {
        Arrow(int id, int x, int y, String s) { super(id, x, y, 12, 13, s); }

        @Override
        public void drawButton(Minecraft mc, int mx, int my, float pt) {
            if (!visible) return;
            hovered = mx >= x && my >= y && mx < x + width && my < y + height;
            Gui.drawRect(x, y, x + width, y + height, hovered ? 0xE03A2D55 : 0xD01C1530);
            Gui.drawRect(x, y, x + width, y + 1, 0xFF5BCEFA); Gui.drawRect(x, y + height - 1, x + width, y + height, 0xFF5BCEFA);
            drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y + 3, hovered ? 0xFFF5A9B8 : 0xFFFFFFFF);
        }
    }
}
