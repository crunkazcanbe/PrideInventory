package com.dogpound.prideinventory;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;

/** config/prideinventory.cfg — every rule of how extra rows unlock, and how the panel and hotbar pages behave. */
public final class Config {
    /** hard ceiling the storage is sized for; maxRows can be anything up to this */
    static final int MAX_ROWS_EVER = 12;
    public static final int MAX_SLOTS = MAX_ROWS_EVER * 9;

    // inventory
    public static int maxRows, startRows, visibleRows;
    public static String panelSide;
    // hotbar pages
    public static int hotbarPages;
    public static boolean hudIndicator, wideHotbar;
    // unlocking by playing (0 = rule off)
    public static int advancementsPerRow, hoursPerRow, blocksMinedPerRow, mobKillsPerRow;
    // buying rows with RealmCoin
    public static boolean buyWithCoins;
    public static long rowPriceCoins;
    public static double priceMultiplier;
    // who simply gets everything
    public static boolean creativeUnlocksAll, opsUnlockAll;
    public static Set<String> alwaysUnlocked = new HashSet<>();
    // death
    public static boolean keepOnDeath;
    // inventory sharing
    public static boolean sharing, shareNotify;
    public static String shareTrigger;
    public static double shareDistance;

    static void load(File file) {
        Configuration c = new Configuration(file);
        String inv = "inventory", pages = "hotbar_pages", unlock = "unlock_by_playing", coins = "buy_with_realmcoin", everyone = "unlock_everything", death = "death";
        c.setCategoryComment(unlock, "Extra rows unlock as you play. Every rule adds rows on its own (they stack), up to maxRows. 0 = that rule is off.\n"
            + "Quests and admins can also hand out rows (/prideinv grant <player> <rows>).");
        maxRows = c.getInt("maxRows", inv, 6, 0, MAX_ROWS_EVER, "Most extra rows (9 slots each) a player can ever have.");
        startRows = c.getInt("startRows", inv, 0, 0, MAX_ROWS_EVER, "Rows everyone has from the start.");
        visibleRows = c.getInt("visibleRows", inv, 3, 1, MAX_ROWS_EVER, "Rows shown at once in the panel; scroll for the rest.");
        panelSide = c.getString("panelSide", inv, "auto", "Where the extra-rows panel sits: auto (below if it fits, else right, else left — "
            + "never off the screen at any GUI scale), below, right or left.", new String[]{"auto", "below", "right", "left"});

        hotbarPages = c.getInt("hotbarPages", pages, 6, 0, MAX_ROWS_EVER, "Most hotbar pages you can flip through (each unlocked row can be a page). "
            + "The hotbar itself stays 9 slots; flipping swaps a row into it. 0 = no pages.");
        hudIndicator = c.getBoolean("hudIndicator", pages, true, "Show \"page 2/5\" and arrows beside the hotbar (normal hotbar only).");
        wideHotbar = c.getBoolean("wideHotbar", pages, true, "Requested: one long hotbar across the whole bottom of the screen, edge to edge — your normal "
            + "9 slots then every UNLOCKED row, left to right. The scroll wheel moves along all of it; stepping onto another row swaps that row into "
            + "the real hotbar behind the scenes, so every other mod still sees a normal hotbar. false = the normal hotbar with page arrows.");

        advancementsPerRow = c.getInt("advancementsPerRow", unlock, 20, 0, 100000, "+1 row for every this many advancements completed (recipe unlocks don't count).");
        hoursPerRow = c.getInt("hoursPerRow", unlock, 5, 0, 100000, "+1 row for every this many hours played.");
        blocksMinedPerRow = c.getInt("blocksMinedPerRow", unlock, 10000, 0, 100000000, "+1 row for every this many blocks mined.");
        mobKillsPerRow = c.getInt("mobKillsPerRow", unlock, 500, 0, 100000000, "+1 row for every this many mobs killed.");

        buyWithCoins = c.getBoolean("enabled", coins, true, "Let players buy the next row with RealmCoin money (/prideinv buy, or the button in the panel).");
        rowPriceCoins = c.getInt("firstRowPrice", coins, 1000, 0, Integer.MAX_VALUE, "Price of the first bought row, in coins.");
        priceMultiplier = c.getFloat("priceMultiplier", coins, 2.0f, 1.0f, 100f, "Each next bought row costs this many times the one before.");

        creativeUnlocksAll = c.getBoolean("creativeUnlocksAll", everyone, true, "Creative mode = every row unlocked (up to maxRows).");
        opsUnlockAll = c.getBoolean("opsUnlockAll", everyone, false, "Server operators always have every row.");
        String[] names = c.getStringList("alwaysUnlockedPlayers", everyone, new String[0], "Player names that always have every row, even in survival.");
        alwaysUnlocked = new HashSet<>();
        for (String n : names) if (!n.trim().isEmpty()) alwaysUnlocked.add(n.trim().toLowerCase(Locale.ROOT));

        keepOnDeath = c.getBoolean("keepExtraRowsOnDeath", death, false, "Keep what's in the extra rows when you die. false = they drop like the rest of your "
            + "inventory (the keepInventory gamerule still keeps everything). Unlocked rows are never lost either way.");
        String share = "inventory_sharing";
        c.setCategoryComment(share, "Right-click a player to see their inventory. Everyone starts LOCKED (look only); the padlock in your inventory,\n"
            + "a key, or /invlock off unlocks yours so others can take and give. /invlock gifts on = they may give you things while locked.");
        sharing = c.getBoolean("enabled", share, true, "Turn inventory sharing on or off for the whole server.");
        shareTrigger = c.getString("openWith", share, "empty_hand", "What opens someone's inventory: empty_hand (right-click with an empty hand, not sneaking), "
            + "sneak_empty_hand (sneak + right-click, empty hand), any_empty_hand. Other mods that use right-click on players and cancel it always win.",
            new String[]{"empty_hand", "sneak_empty_hand", "any_empty_hand"});
        shareDistance = c.getFloat("maxDistance", share, 6f, 2f, 64f, "The view closes when the two players are farther apart than this (blocks).");
        shareNotify = c.getBoolean("notifyOwner", share, true, "Tell the owner in chat when someone opens, takes or gives (always written to /invlock log).");
        if (visibleRows > Math.max(1, maxRows)) visibleRows = Math.max(1, maxRows);
        c.save();
    }

    static String describe() {
        return "max " + maxRows + " rows, start " + startRows + ", pages " + hotbarPages + ", rules adv/" + advancementsPerRow + " h/" + hoursPerRow
            + " mined/" + blocksMinedPerRow + " kills/" + mobKillsPerRow + (buyWithCoins ? ", buy from " + rowPriceCoins + " coins" : "")
            + (creativeUnlocksAll ? ", creative=all" : "") + (alwaysUnlocked.isEmpty() ? "" : ", always: " + Arrays.toString(alwaysUnlocked.toArray()));
    }
}
