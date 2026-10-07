package com.dogpound.prideinventory;

import java.io.IOException;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

/** Someone else's inventory: header with their lock state, their slots in a coloured frame, extra rows (scroll), yours below. */
@SideOnly(Side.CLIENT)
public class GuiShare extends GuiContainer {
    static final int LOCKED = 0xFFF5A9B8, OPEN = 0xFF7CFC9A;
    private static final int EXTRA_Y = 129;
    private final ShareContainer c;
    private final String owner;
    private int shown, scroll, yourTop;

    static Object create(EntityPlayer p, World w, int entityId, int rows) {
        Entity e = w.getEntityByID(entityId);
        return new GuiShare(new ShareContainer(p, rows), e != null ? e.getName() : "Player");
    }

    private GuiShare(ShareContainer c, String owner) {
        super(c);
        this.c = c;
        this.owner = owner;
        xSize = 176;
    }

    private int heightFor(int rows) {
        return (c.rows > 0 ? EXTRA_Y + rows * 18 + 4 : EXTRA_Y - 2) + 11 + 58 + 18 + 6;
    }

    @Override
    public void initGui() {
        shown = Math.min(c.rows, 3);
        while (shown > 1 && heightFor(shown) > height - 4) shown--;   // big GUI scales: fewer rows, scroll for the rest
        ySize = heightFor(shown);
        yourTop = ySize - 6 - 18 - 58;
        super.initGui();
        place();
    }

    /** visible extra rows into place, the rest hidden; your own inventory under them */
    private void place() {
        scroll = Math.max(0, Math.min(scroll, c.rows - shown));
        for (int i = 0; i < c.inventorySlots.size(); i++) {
            Slot s = c.inventorySlots.get(i);
            if (i >= ShareContainer.EXTRA && i < c.ownerSlots) {
                int k = i - ShareContainer.EXTRA, row = k / 9;
                boolean on = row >= scroll && row < scroll + shown;
                s.xPos = on ? 8 + k % 9 * 18 : ExtraSlot.HIDDEN;
                s.yPos = on ? EXTRA_Y + (row - scroll) * 18 : ExtraSlot.HIDDEN;
            } else if (i >= c.ownerSlots) {
                int k = i - c.ownerSlots;
                s.xPos = 8 + k % 9 * 18;
                s.yPos = k < 27 ? yourTop + k / 9 * 18 : yourTop + 58;
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0 && c.rows > shown) { scroll += d > 0 ? -1 : 1; place(); }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        super.drawScreen(mx, my, pt);
        renderHoveredToolTip(mx, my);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float pt, int mx, int my) {
        int x = guiLeft, y = guiTop, w = xSize, h = ySize;
        Gui.drawRect(x, y, x + w, y + h, 0xFFC6C6C6);                          // vanilla grey, bevelled
        Gui.drawRect(x, y, x + w, y + 1, 0xFFFFFFFF); Gui.drawRect(x, y, x + 1, y + h, 0xFFFFFFFF);
        Gui.drawRect(x + w - 1, y, x + w, y + h, 0xFF555555); Gui.drawRect(x, y + h - 1, x + w, y + h, 0xFF555555);
        // their part, framed in their lock colour
        int bottom = c.rows > 0 ? EXTRA_Y + shown * 18 + 1 : EXTRA_Y - 3;
        int col = (c.flags & 1) != 0 ? OPEN : LOCKED;
        Gui.drawRect(x + 5, y + 24, x + w - 5, y + 25, col); Gui.drawRect(x + 5, y + bottom, x + w - 5, y + bottom + 1, col);
        Gui.drawRect(x + 5, y + 24, x + 6, y + bottom + 1, col); Gui.drawRect(x + w - 6, y + 24, x + w - 5, y + bottom + 1, col);
        for (Slot s : c.inventorySlots) {
            if (!s.isEnabled()) continue;
            int sx = x + s.xPos - 1, sy = y + s.yPos - 1;
            Gui.drawRect(sx, sy, sx + 18, sy + 18, 0xFF8B8B8B);
            Gui.drawRect(sx, sy, sx + 17, sy + 1, 0xFF373737); Gui.drawRect(sx, sy, sx + 1, sy + 17, 0xFF373737);
            Gui.drawRect(sx + 17, sy, sx + 18, sy + 18, 0xFFFFFFFF); Gui.drawRect(sx, sy + 17, sx + 18, sy + 18, 0xFFFFFFFF);
        }
        if (c.rows > shown) {                                                  // scroll bar by the extra rows
            int track = shown * 18, bx = x + w - 5, by = y + EXTRA_Y;
            Gui.drawRect(bx, by, bx + 3, by + track, 0xFF555555);
            int knob = Math.max(6, track * shown / c.rows);
            int ky = by + (track - knob) * scroll / Math.max(1, c.rows - shown);
            Gui.drawRect(bx, ky, bx + 3, ky + knob, LOCKED);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        boolean open = (c.flags & 1) != 0, gifts = (c.flags & 2) != 0;
        fontRenderer.drawString(owner + "'s inventory", 8, 5, 0xFF404040);
        drawLock(8, 13, open, open ? 0xFF2E9E4A : 0xFFC0405A);
        fontRenderer.drawString(open ? "unlocked — take and give" : gifts ? "locked — you can only give" : "locked (view only)",
            19, 15, open ? 0xFF2E7D3A : 0xFF9A3048);
        fontRenderer.drawString("Your inventory", 8, yourTop - 10, 0xFF404040);
    }

    /** tiny pixel padlock, 7×10 from (x, y); open = shackle lifted out on the left */
    static void drawLock(int x, int y, boolean open, int color) {
        int sy = open ? y - 2 : y;
        Gui.drawRect(x, y + 4, x + 7, y + 10, color);                          // body
        Gui.drawRect(x + 1, sy, x + 6, sy + 1, color);                         // top of the shackle
        Gui.drawRect(x + 1, sy, x + 2, open ? sy + 3 : y + 4, color);          // left leg (out of the body when open)
        Gui.drawRect(x + 5, sy, x + 6, y + 4, color);                          // right leg
        Gui.drawRect(x + 3, y + 6, x + 4, y + 8, 0xFF000000);                  // keyhole
    }
}
