package plugily.projects.murdermystery.arena;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XSound;
import plugily.projects.murdermystery.Main;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the Murderer rampage and cooldown XP bar timers.
 */
public final class MurdererTimerManager {

  private static Main plugin;
  private static final Map<UUID, BukkitTask> playerTimers = new ConcurrentHashMap<>();
  private static int rampageSeconds;
  private static int cooldownSeconds;

  private MurdererTimerManager() {}

  public static void init(Main mainPlugin) {
    plugin = mainPlugin;
    refreshConfigValues();
  }

  public static void refreshConfigValues() {
    if (plugin == null) return;
    rampageSeconds = plugin.getConfig().getInt("Murderer.Rampage-Seconds", 10);
    cooldownSeconds = plugin.getConfig().getInt("Murderer.Cooldown-Seconds", 30);
  }

  /**
   * Start or refresh the rampage timer for a murderer. When rampage ends, starts cooldown.
   */
  public static void startOrRefreshRampage(Player murderer) {
    if (plugin == null || murderer == null) {
      return;
    }

    refreshConfigValues();

    // Set/refresh rampage cooldown counter in user storage (decremented by core each second)
    IUser user = plugin.getUserManager().getUser(murderer);
    user.setCooldown("murderer_rampage", rampageSeconds);

    // ensure any old timer is cancelled, then start a new one
    cancelTimer(murderer.getUniqueId());

    BukkitTask task = new BukkitRunnable() {
      private boolean inRampagePhase = true;
      private float lastExp = -1f;

      @Override
      public void run() {
        if (!murderer.isOnline() || plugin.getArenaRegistry().getArena(murderer) == null) {
          resetExpBarSafe(murderer);
          cancelSelf(murderer.getUniqueId());
          return;
        }

        // If player became spectator, stop
        IUser loopUser = plugin.getUserManager().getUser(murderer);
        if (loopUser.isSpectator()) {
          resetExpBarSafe(murderer);
          cancelSelf(murderer.getUniqueId());
          return;
        }

        int remaining = (int) Math.ceil(loopUser.getCooldown(inRampagePhase ? "murderer_rampage" : "murderer_cooldown"));
        int total = inRampagePhase ? rampageSeconds : cooldownSeconds;

        // Update XP bar gradually (decreasing)
        float progress = total <= 0 ? 0f : Math.max(0f, Math.min(1f, remaining / (float) total));
        if (progress != lastExp) {
          try {
            murderer.setExp(progress);
          } catch (Throwable ignored) {
          }
          lastExp = progress;
        }

        // Transition logic
        if (remaining <= 0) {
          if (inRampagePhase) {
            // switch to cooldown phase
            inRampagePhase = false;
            loopUser.setCooldown("murderer_cooldown", cooldownSeconds);
            // notify murderer with a noticeable sound
            XSound.ENTITY_ENDER_DRAGON_GROWL.play(murderer.getLocation(), 1f, 1f);
          } else {
            // cooldown done -> clear and stop
            resetExpBarSafe(murderer);
            cancelSelf(murderer.getUniqueId());
          }
        }
      }

      private void cancelSelf(UUID uuid) {
        Bukkit.getScheduler().runTask(plugin, () -> cancelTimer(uuid));
        cancel();
      }
    }.runTaskTimer(plugin, 0L, 2L); // update every 2 ticks for smoothness

    playerTimers.put(murderer.getUniqueId(), task);
  }

  public static void cancelFor(Player player) {
    if (player == null) return;
    cancelTimer(player.getUniqueId());
    resetExpBarSafe(player);
  }

  private static void resetExpBarSafe(Player player) {
    try {
      player.setExp(0f);
    } catch (Throwable ignored) {
    }
  }

  private static void cancelTimer(UUID uuid) {
    BukkitTask previous = playerTimers.remove(uuid);
    if (previous != null) {
      previous.cancel();
    }
  }
}
