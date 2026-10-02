package com.dogpound.prideinventory;

import java.util.Locale;

import net.minecraft.advancements.Advancement;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.stats.StatisticsManagerServer;
import net.minecraft.util.text.TextComponentString;

/** How many extra rows a player has right now, from Config's rules. Server side; the result is synced to the client. */
public final class Unlocks {
    private Unlocks() {}

    static boolean everything(EntityPlayerMP p) {
        return (Config.creativeUnlocksAll && p.isCreative())
            || Config.alwaysUnlocked.contains(p.getName().toLowerCase(Locale.ROOT))
            || (Config.opsUnlockAll && p.canUseCommand(2, "prideinv"));
    }

    /** recount everything for this player, store it, rotate hotbar pages back if the page count shrank, and sync */
    public static void update(EntityPlayerMP p) {
        ExtraInv inv = ExtraInv.of(p);
        if (inv == null) return;
        StatisticsManagerServer st = p.getStatFile();
        inv.hours = st.readStat(StatList.PLAY_ONE_MINUTE) / 20 / 3600; // the stat counts ticks
        inv.mobKills = st.readStat(StatList.MOB_KILLS);
        int mined = 0;
        for (StatBase s : StatList.MINE_BLOCK_STATS) mined += st.readStat(s);
        inv.blocksMined = mined;
        int adv = 0;
        for (Advancement a : p.getServer().getAdvancementManager().getAdvancements())
            if (a.getDisplay() != null && p.getAdvancements().getProgress(a).isDone()) adv++;  // no display = recipe unlock
        inv.advancements = adv;

        inv.wasEverything = everything(p);
        int rows = inv.wasEverything ? Config.maxRows : earned(inv.bought, inv.granted, adv, inv.hours, mined, inv.mobKills);
        int oldPages = Pages.pageRows(inv.unlocked), newPages = Pages.pageRows(rows);
        if (oldPages != newPages && inv.page != 0) Pages.home(p, inv, oldPages);        // put the real hotbar back first
        boolean more = rows > inv.unlocked && inv.unlocked >= 0 && p.ticksExisted > 100; // not on login
        inv.unlocked = rows;
        if (more) p.sendMessage(new TextComponentString("§d✦ New inventory row unlocked! §7(" + rows + " extra rows)"));
        Net.sync(p);
    }

    /** the rows a (non-creative) player has earned by the config's rules */
    static int earned(int bought, int granted, int advancements, int hours, int mined, int kills) {
        int rows = Config.startRows + bought + granted;
        if (Config.advancementsPerRow > 0) rows += advancements / Config.advancementsPerRow;
        if (Config.hoursPerRow > 0) rows += hours / Config.hoursPerRow;
        if (Config.blocksMinedPerRow > 0) rows += mined / Config.blocksMinedPerRow;
        if (Config.mobKillsPerRow > 0) rows += kills / Config.mobKillsPerRow;
        return Math.max(0, Math.min(rows, Config.maxRows));
    }

    /** RealmCoin price of the next bought row, in coins */
    static long nextPrice(ExtraInv inv) {
        return Math.round(Config.rowPriceCoins * Math.pow(Config.priceMultiplier, inv.bought));
    }
}
