package com.dogpound.prideinventory;

import java.io.IOException;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** Requested: type a slot number (1 to every unlocked slot) and that slot is selected. Enter = go, Esc = cancel. */
final class GuiSlotNumber extends GuiScreen {
    private GuiTextField box;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        box = new GuiTextField(0, fontRenderer, width / 2 - 30, height - 60, 60, 16);
        box.setMaxStringLength(3);
        box.setFocused(true);
        box.setValidator(t -> t.isEmpty() || t.matches("\\d{1,3}"));
    }

    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (!box.getText().isEmpty()) WideHotbar.select(Integer.parseInt(box.getText()));
            mc.displayGuiScreen(null);
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
        box.textboxKeyTyped(c, key);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        String t = "Slot number (1-" + WideHotbar.slots() + ")";
        drawRect(width / 2 - 70, height - 78, width / 2 + 70, height - 38, 0xCC1A1424);
        drawRect(width / 2 - 70, height - 78, width / 2 + 70, height - 77, 0xFFB46CFF);
        drawCenteredString(fontRenderer, t, width / 2, height - 72, 0xFFFFFF);
        box.drawTextBox();
    }
}
