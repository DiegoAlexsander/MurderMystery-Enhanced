/*
 * MurderMystery - Find the murderer, kill him and survive!
 * Copyright (c) 2022  Plugily Projects - maintained by Tigerpanzer_02 and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package plugily.projects.murdermystery.arena;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;
import plugily.projects.minigamesbox.api.arena.IArenaState;
import plugily.projects.minigamesbox.api.arena.IPluginArena;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.arena.PluginArenaEvents;
import plugily.projects.minigamesbox.classic.handlers.items.SpecialItem;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.handlers.language.TitleBuilder;
import plugily.projects.minigamesbox.classic.utils.misc.complement.ComplementAccessor;
import plugily.projects.minigamesbox.classic.utils.version.ServerVersion;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.minigamesbox.classic.utils.version.events.api.PlugilyEntityPickupItemEvent;
import plugily.projects.minigamesbox.classic.utils.version.events.api.PlugilyPlayerPickupArrow;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XPotion;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XSound;
import plugily.projects.minigamesbox.classic.utils.version.xseries.inventory.XInventoryView;
import plugily.projects.murdermystery.Main;
import plugily.projects.murdermystery.arena.managers.MapRestorerManager;
import plugily.projects.murdermystery.arena.role.Role;
import plugily.projects.murdermystery.arena.special.pray.PrayerRegistry;
import plugily.projects.murdermystery.utils.ItemPosition;

/**
 * @author Plajer
 * <p>Created at 13.03.2018
 */
public class ArenaEvents extends PluginArenaEvents {

  private final Main plugin;

  public ArenaEvents(Main plugin) {
    super(plugin);
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
  }

  private void triggerLightsSabotage(Arena arena, Player murderer) {
    // Remove all GOLD from murderer's inventory upon sabotage
    try {
      IUser murdererUser = plugin.getUserManager().getUser(murderer);
      murderer.getInventory().setItem(ItemPosition.GOLD_INGOTS.getMurdererItemPosition(), null);
      murdererUser.setStatistic("LOCAL_GOLD", 0);
    } catch (Throwable ignored) {
      // best-effort inventory cleanup
    }

    // Get sabotage configuration
    String sabotageMode = plugin.getConfig().getString("Gold.Sabotage.Mode", "TEMPORARY").toUpperCase();
    int sabotageDuration = plugin.getConfig().getInt("Gold.Sabotage.Duration", 30);
    String messageType = plugin.getConfig().getString("Gold.Sabotage.Message-Type", "TITLE").toUpperCase();
    boolean useTitle = messageType.equals("TITLE");
    boolean isPermanent = sabotageMode.equals("PERMANENT");

    // Apply effects to all players
    for (Player p : arena.getPlayers()) {
      if (p.equals(murderer)) {
        // Murderer gets green title/message and optionally reinforced night vision
        String msgKey = isPermanent ? "IN_GAME_MESSAGES_ARENA_PLAYING_SABOTAGE_PERMANENT_MURDERER_ACTIVATED" 
                                    : "IN_GAME_MESSAGES_ARENA_PLAYING_SABOTAGE_MURDERER_ACTIVATED";
        if (useTitle) {
          String murdererActivatedMsg = new MessageBuilder(msgKey).asKey().player(p).arena(arena).build();
          VersionUtils.sendTitles(p, murdererActivatedMsg, null, 5, 40, 10);
        } else {
          new MessageBuilder(msgKey).asKey().player(p).arena(arena).sendPlayer();
        }
        
        int nightVisionDuration = isPermanent ? Integer.MAX_VALUE : (20 * (sabotageDuration + 5));
        try { XPotion.NIGHT_VISION.buildPotionEffect(nightVisionDuration, 1).apply(p); } catch (Throwable ignored) {}
        continue;
      }
      
      // Send sabotage message to innocents/detectives
      String msgKey = isPermanent ? "IN_GAME_MESSAGES_ARENA_PLAYING_SABOTAGE_PERMANENT_INNOCENT_ACTIVATED"
                                  : "IN_GAME_MESSAGES_ARENA_PLAYING_SABOTAGE_INNOCENT_ACTIVATED";
      if (useTitle) {
        String innocentActivatedMsg = new MessageBuilder(msgKey).asKey().player(p).arena(arena).build();
        VersionUtils.sendTitles(p, innocentActivatedMsg, null, 5, 40, 10);
      } else {
        new MessageBuilder(msgKey).asKey().player(p).arena(arena).sendPlayer();
      }
      
      // Apply darkness/blindness effect
      int effectDuration = isPermanent ? Integer.MAX_VALUE : (20 * sabotageDuration);
      if (plugily.projects.minigamesbox.classic.utils.version.ServerVersion.Version.isCurrentEqualOrHigher(plugily.projects.minigamesbox.classic.utils.version.ServerVersion.Version.v1_19)) {
        try {
          p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS, effectDuration, 0, true, false, true));
        } catch (Throwable ignored) {
          try { XPotion.BLINDNESS.buildPotionEffect(effectDuration, 1).apply(p); } catch (Throwable ignored2) {}
        }
      } else {
        try { XPotion.BLINDNESS.buildPotionEffect(effectDuration, 1).apply(p); } catch (Throwable ignored) {}
      }
    }

    if (isPermanent) {
      // Activate permanent sabotage mode
      arena.setSabotageActive(true);
      
      // Start glowstone dust spawning only if using CIRCUIT_BREAKERS or BOTH modes
      String restorationSystem = plugin.getConfig().getString("Gold.Sabotage.Restoration.System", "GOLD_COLLECTION").toUpperCase();
      if (restorationSystem.equals("CIRCUIT_BREAKERS") || restorationSystem.equals("BOTH")) {
        startGlowstoneDustSpawning(arena);
      }
    } else {
      // Temporary mode - auto-restore after duration
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        restoreLights(arena, murderer);
      }, 20L * sabotageDuration);
    }
  }

  private void startGlowstoneDustSpawning(Arena arena) {
    int spawnInterval = plugin.getConfig().getInt("Gold.Glowstone-Dust.Spawn-Interval", 3);
    
    BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
      if (!arena.isSabotageActive() || arena.getArenaState() != IArenaState.IN_GAME) {
        if (arena.getGlowstoneDustSpawnTask() != null) {
          arena.getGlowstoneDustSpawnTask().cancel();
          arena.setGlowstoneDustSpawnTask(null);
        }
        return;
      }

      arena.setSpawnGlowstoneDustTimer(arena.getSpawnGlowstoneDustTimer() + 1);
      
      if (arena.getSpawnGlowstoneDustTimer() >= spawnInterval) {
        arena.setSpawnGlowstoneDustTimer(0);
        spawnGlowstoneDust(arena);
      }
    }, 20L, 20L);
    
    arena.setGlowstoneDustSpawnTask(task);
  }

  private void spawnGlowstoneDust(Arena arena) {
    if (arena.getGoldSpawnPoints().isEmpty()) {
      return;
    }

    boolean spawnerMode = plugin.getConfig().getBoolean("Gold.Glowstone-Dust.Spawner-Mode", false);
    boolean limiter = plugin.getConfig().getBoolean("Gold.Glowstone-Dust.Limiter", false);
    boolean multiple = plugin.getConfig().getBoolean("Gold.Glowstone-Dust.Multiple", false);

    if (spawnerMode) {
      // Spawn at all spawners
      for (Location location : arena.getGoldSpawnPoints()) {
        if (!limiter || !isGlowstoneDustAtLocation(arena, location) || multiple) {
          spawnGlowstoneDustAt(arena, location);
        }
      }
    } else {
      // Spawn at random spawner
      Location randomLocation = arena.getGoldSpawnPoints().get(plugin.getRandom().nextInt(arena.getGoldSpawnPoints().size()));
      if (!limiter || !isGlowstoneDustAtLocation(arena, randomLocation) || multiple) {
        spawnGlowstoneDustAt(arena, randomLocation);
      }
    }
  }

  private boolean isGlowstoneDustAtLocation(Arena arena, Location location) {
    for (Item item : arena.getGlowstoneDustSpawned()) {
      if (item.getLocation().distance(location) < 1.0) {
        return true;
      }
    }
    return false;
  }

  private void spawnGlowstoneDustAt(Arena arena, Location location) {
    Item glowstoneDust = location.getWorld().dropItem(location, new org.bukkit.inventory.ItemStack(org.bukkit.Material.GLOWSTONE_DUST, 1));
    glowstoneDust.setPickupDelay(0);
    arena.getGlowstoneDustSpawned().add(glowstoneDust);
  }

  public void restoreLights(Arena arena, Player murderer) {
    if (!arena.isSabotageActive() && !plugin.getConfig().getString("Gold.Sabotage.Mode", "TEMPORARY").equalsIgnoreCase("TEMPORARY")) {
      return; // Already restored or not in permanent mode
    }

    String messageType = plugin.getConfig().getString("Gold.Sabotage.Message-Type", "TITLE").toUpperCase();
    boolean useTitle = messageType.equals("TITLE");

    // Remove effects and send restore messages
    for (Player p : arena.getPlayers()) {
      // Remove darkness/blindness effects
      p.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
      if (plugily.projects.minigamesbox.classic.utils.version.ServerVersion.Version.isCurrentEqualOrHigher(plugily.projects.minigamesbox.classic.utils.version.ServerVersion.Version.v1_19)) {
        try {
          p.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS);
        } catch (Throwable ignored) {}
      }

      if (p.equals(murderer) || Role.isRole(Role.MURDERER, plugin.getUserManager().getUser(p), arena)) {
        // Remove night vision from murderer
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
        
        if (useTitle) {
          String murdererRestoredMsg = new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_SABOTAGE_MURDERER_RESTORED")
            .asKey().player(p).arena(arena).build();
          VersionUtils.sendTitles(p, murdererRestoredMsg, null, 5, 20, 5);
        } else {
          new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_SABOTAGE_MURDERER_RESTORED")
            .asKey().player(p).arena(arena).sendPlayer();
        }
      } else {
        if (useTitle) {
          String innocentRestoredMsg = new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_SABOTAGE_INNOCENT_RESTORED")
            .asKey().player(p).arena(arena).build();
          VersionUtils.sendTitles(p, innocentRestoredMsg, null, 5, 20, 5);
        } else {
          new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_SABOTAGE_INNOCENT_RESTORED")
            .asKey().player(p).arena(arena).sendPlayer();
        }
      }
    }

    // Deactivate sabotage (this will also stop glowstone dust spawning)
    arena.setSabotageActive(false);
  }

  @Override
  public void handleIngameVoidDeath(Player victim, IPluginArena arena) {
    Arena pluginArena = plugin.getArenaRegistry().getArena(arena.getId());
    if(pluginArena == null) {
      return;
    }
    victim.damage(1000.0);
    if(arena.getArenaState() == IArenaState.IN_GAME) {
      VersionUtils.teleport(victim, pluginArena.getPlayerSpawnPoints().get(0));
    }
  }

  @EventHandler
  public void onBowShot(EntityShootBowEvent event) {
    if(event.getEntityType() != EntityType.PLAYER) {
      return;
    }
    Player player = (Player) event.getEntity();
    IUser user = plugin.getUserManager().getUser(player);
    if(!Role.isRole(Role.ANY_DETECTIVE, user)) {
      return;
    }
    if(user.getCooldown("bow_shot") > 0) {
      event.setCancelled(true);
      return;
    }
    int bowCooldown = plugin.getConfig().getInt("Bow.Cooldown", 5);
    if(bowCooldown <= 0) {
      return;
    }
    user.setCooldown("bow_shot", bowCooldown);
    plugin.getBukkitHelper().applyActionBarCooldown(player, bowCooldown);
    VersionUtils.setMaterialCooldown(player, event.getBow().getType(), 20 * (plugin.getConfig().getInt("Bow.Cooldown", 5)));
  }

  @EventHandler
  public void onArrowPickup(PlugilyPlayerPickupArrow e) {
    if(plugin.getArenaRegistry().isInArena(e.getPlayer())) {
      e.getItem().remove();
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onItemPickup(PlugilyEntityPickupItemEvent e) {
    if(!(e.getEntity() instanceof Player)) {
      return;
    }
    Player player = (Player) e.getEntity();
    Arena arena = plugin.getArenaRegistry().getArena(player);
    if(arena == null) {
      return;
    }
    IUser user = plugin.getUserManager().getUser(player);
    e.setCancelled(true);
    if(arena.getBowHologram() != null
      && e.getItem().equals(arena.getBowHologram().getEntityItem())) {
      if(!user.isSpectator() && Role.isRole(Role.INNOCENT, user, arena)) {
        XSound.BLOCK_LAVA_POP.play(player.getLocation(), 1F, 2F);

        ((MapRestorerManager) arena.getMapRestorerManager()).removeBowHolo();
        e.getItem().remove();

        for(Player loopPlayer : arena.getPlayersLeft()) {
          IUser loopUser = plugin.getUserManager().getUser(loopPlayer);
          if(Role.isRole(Role.INNOCENT, loopUser)) {
            ItemPosition.setItem(loopUser, ItemPosition.BOW_LOCATOR, new ItemStack(Material.AIR, 1));
          }
        }

        arena.setCharacter(Arena.CharacterType.FAKE_DETECTIVE, player);
        ItemPosition.setItem(user, ItemPosition.BOW, new ItemStack(Material.BOW, 1));
        ItemPosition.setItem(user, ItemPosition.INFINITE_ARROWS, new ItemStack(Material.ARROW, plugin.getConfig().getInt("Bow.Amount.Arrows.Fake", 3)));
        new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_BOW_PICKUP").asKey().player(player).arena(arena).sendArena();
      }

      return;
    }
    
    // Handle glowstone dust pickup BEFORE gold check
    if (e.getItem().getItemStack().getType() == Material.GLOWSTONE_DUST) {
      if (!arena.isSabotageActive() || Role.isRole(Role.MURDERER, user, arena)) {
        e.setCancelled(true);
        return;
      }
      
      e.getItem().remove();
      XSound.BLOCK_GLASS_BREAK.play(player.getLocation(), 1, 2);
      arena.getGlowstoneDustSpawned().remove(e.getItem());
      
      ItemStack glowstoneDustStack = new ItemStack(Material.GLOWSTONE_DUST, e.getItem().getItemStack().getAmount());
      player.getInventory().addItem(glowstoneDustStack);
      
      new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_GLOWSTONE_DUST_PICKUP")
        .asKey()
        .player(player)
        .arena(arena)
        .sendPlayer();
      return;
    }

    if(e.getItem().getItemStack().getType() != Material.GOLD_INGOT) {
      return;
    }

    if(user.isSpectator() || arena.getArenaState() != IArenaState.IN_GAME) {
      return;
    }

    if(PrayerRegistry.getBan().contains(player)) {
      e.setCancelled(true);
      return;
    }

    e.getItem().remove();

    XSound.BLOCK_LAVA_POP.play(player.getLocation(), 1, 1);
    arena.getGoldSpawned().remove(e.getItem());

    ItemStack stack = new ItemStack(Material.GOLD_INGOT, e.getItem().getItemStack().getAmount());
    if(PrayerRegistry.getRush().contains(player)) {
      stack.setAmount(3 * e.getItem().getItemStack().getAmount());
    }

    ItemPosition.addItem(user, ItemPosition.GOLD_INGOTS, stack);
    user.adjustStatistic("LOCAL_GOLD", stack.getAmount());
    ArenaUtils.addScore(user, ArenaUtils.ScoreAction.GOLD_PICKUP, stack.getAmount());

    new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_SCORE_GOLD").asKey().player(player).arena(arena).sendPlayer();
    plugin.getRewardsHandler().performReward(player, plugin.getRewardsHandler().getRewardType("GOLD_PICKUP"));

    // Check if sabotage is active and player is innocent/detective
    if (arena.isSabotageActive() && !Role.isRole(Role.MURDERER, user, arena)) {
      String restorationSystem = plugin.getConfig().getString("Gold.Sabotage.Restoration.System", "GOLD_COLLECTION").toUpperCase();
      
      if (restorationSystem.equals("GOLD_COLLECTION") || restorationSystem.equals("BOTH")) {
        arena.addGlobalGoldCollected(stack.getAmount());
        int required = plugin.getConfig().getInt("Gold.Sabotage.Restoration.Gold-Collection.Amount", 50);
        
        // Send progress message to ALL players in the arena (broadcast)
        new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_SABOTAGE_GOLD_PROGRESS")
          .asKey()
          .integer(arena.getGlobalGoldCollected())
          .value(String.valueOf(required))
          .arena(arena)
          .sendArena();
        
        // Check if restoration threshold reached
        if (arena.getGlobalGoldCollected() >= required) {
          boolean canRestore = true;
          
          // If BOTH system, also check circuit breakers
          if (restorationSystem.equals("BOTH")) {
            int totalBreakers = arena.getCircuitBreakerCount();
            int activatedBreakers = arena.getActivatedCircuitBreakers().size();
            canRestore = (activatedBreakers >= totalBreakers && totalBreakers > 0);
          }
          
          if (canRestore) {
            restoreLights(arena, null);
            // Broadcast restoration success
            for (Player p : arena.getPlayers()) {
              new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_SABOTAGE_LIGHTS_RESTORED_BROADCAST")
                .asKey()
                .player(p)
                .arena(arena)
                .sendPlayer();
            }
          }
        }
      }
    }

    if(Role.isRole(Role.ANY_DETECTIVE, user, arena)) {
      ItemPosition.addItem(user, ItemPosition.ARROWS, new ItemStack(Material.ARROW, e.getItem().getItemStack().getAmount() * plugin.getConfig().getInt("Bow.Amount.Arrows.Detective", 3)));
      return;
    }

    // Grant bow on gold threshold only for non-murderers
    if(!Role.isRole(Role.MURDERER, user, arena)
      && user.getStatistic("LOCAL_GOLD") >= plugin.getConfig().getInt("Gold.Amount.Bow", 10)) {
      user.setStatistic("LOCAL_GOLD", 0);
      new TitleBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_BOW_SHOT_TITLE")
        .asKey()
        .player(player)
        .arena(arena)
        .sendPlayer();
      ItemPosition.setItem(user, ItemPosition.BOW, new ItemStack(Material.BOW, 1));
      ItemPosition.addItem(
        user,
        ItemPosition.ARROWS,
        new ItemStack(Material.ARROW, plugin.getConfig().getInt("Bow.Amount.Arrows.Gold", 3)));
      player
        .getInventory()
        .setItem(
          /* same for all roles */ ItemPosition.GOLD_INGOTS.getOtherRolesItemPosition(),
          null);
    }
    // If a murderer hits threshold, trigger Lights Sabotage event
    if(Role.isRole(Role.MURDERER, user, arena)
      && user.getStatistic("LOCAL_GOLD") >= plugin.getConfig().getInt("Gold.Amount.Sabotage", 10)
      && plugin.getConfig().getBoolean("Gold.Sabotage.Enabled", true)) {
      // Do not reset their gold; they keep collecting
      triggerLightsSabotage(arena, player);
    }
  }


  @EventHandler
  public void onMurdererDamage(EntityDamageByEntityEvent e) {
    if(!(e.getDamager() instanceof Player) || e.getEntityType() != EntityType.PLAYER) {
      return;
    }
    Player attacker = (Player) e.getDamager();
    IUser userAttacker = plugin.getUserManager().getUser(attacker);
    Player victim = (Player) e.getEntity();
    IUser userVictim = plugin.getUserManager().getUser(victim);
    if(!ArenaUtils.areInSameArena(attacker, victim)) {
      return;
    }
    //we are killing player via damage() method so event can be cancelled safely, will work for detective damage murderer and others
    e.setCancelled(true);

    //better check this for future even if anyone else cannot use sword
    if(!Role.isRole(Role.MURDERER, userAttacker)) {
      return;
    }

    //check if victim is murderer
    if(Role.isRole(Role.MURDERER, userVictim)) {
      return;
    }
    if(VersionUtils.getItemInHand(attacker) == null || plugin.getSwordSkinManager().getMurdererSword(attacker) == null) {
      return;
    }
    //just don't kill user if item isn't murderer sword
    if(VersionUtils.getItemInHand(attacker).getType() != plugin.getSwordSkinManager().getMurdererSword(attacker).getType()) {
      return;
    }

    // prevent hits during global murderer cooldown
    if(plugin.getUserManager().getUser(attacker).getCooldown("murderer_cooldown") > 0) {
      int remain = (int) Math.ceil(plugin.getUserManager().getUser(attacker).getCooldown("murderer_cooldown"));
      new MessageBuilder("IN_GAME_MESSAGES_ARENA_SWORD_ON_COOLDOWN")
        .asKey()
        .integer(remain)
        .player(attacker)
        .sendPlayer();
      return;
    }

    //check if sword has cooldown
    if(ServerVersion.Version.isCurrentLower(ServerVersion.Version.v1_11)) {
      if(plugin.getUserManager().getUser(attacker).getCooldown("sword_attack") > 0) {
        return;
      }
    } else if(attacker.hasCooldown(plugin.getSwordSkinManager().getMurdererSword(attacker).getType())) {
      return;
    }

    if(Role.isRole(Role.MURDERER, userVictim)) {
      plugin.getRewardsHandler().performReward(attacker, plugin.getRewardsHandler().getRewardType("KILL_MURDERER"));
    } else if(Role.isRole(Role.ANY_DETECTIVE, userVictim)) {
      plugin.getRewardsHandler().performReward(attacker, plugin.getRewardsHandler().getRewardType("KILL_DETECTIVE"));
    }

    XSound.ENTITY_PLAYER_DEATH.play(victim.getLocation());
    victim.damage(100.0);

    IUser user = plugin.getUserManager().getUser(attacker);

    user.adjustStatistic("KILLS", 1);
    user.adjustStatistic("LOCAL_KILLS", 1);
    ArenaUtils.addScore(user, ArenaUtils.ScoreAction.KILL_PLAYER, 0);

    Arena arena = plugin.getArenaRegistry().getArena(attacker);
    if(Role.isRole(Role.ANY_DETECTIVE, userVictim) && arena.lastAliveDetective()) {
      //if already true, no effect is done :)
      arena.setDetectiveDead(true);
      if(Role.isRole(Role.FAKE_DETECTIVE, userVictim)) {
        arena.setCharacter(Arena.CharacterType.FAKE_DETECTIVE, null);
      }
      ArenaUtils.dropBowAndAnnounce(arena, victim);
    }

    // Start or refresh Murderer rampage on every kill
    MurdererTimerManager.startOrRefreshRampage(attacker);
  }


  @EventHandler
  public void onArrowDamage(EntityDamageByEntityEvent e) {
    if(!(e.getDamager() instanceof Arrow)) {
      return;
    }
    if(!(((Arrow) e.getDamager()).getShooter() instanceof Player)) {
      return;
    }
    Player attacker = (Player) ((Arrow) e.getDamager()).getShooter();
    IUser userAttacker = plugin.getUserManager().getUser(attacker);
    if(plugin.getArenaRegistry().isInArena(attacker)) {
      e.setCancelled(true);
      e.getDamager().remove();
    }
    if(e.getEntityType() != EntityType.PLAYER) {
      return;
    }
    Player victim = (Player) e.getEntity();
    IUser userVictim = plugin.getUserManager().getUser(victim);
    if(!ArenaUtils.areInSameArena(attacker, victim)) {
      return;
    }
    //we won't allow to suicide
    if(attacker.equals(victim)) {
      e.setCancelled(true);
      return;
    }
    //dont kill murderer on bow damage if attacker is murderer
    if(Role.isRole(Role.MURDERER, userAttacker) && Role.isRole(Role.MURDERER, userVictim)) {
      e.setCancelled(true);
      return;
    }
    Arena arena = plugin.getArenaRegistry().getArena(attacker);
    if (arena == null) {
      return;
    }
    //we need to set it before the victim die, because of hero character
    if(Role.isRole(Role.MURDERER, userVictim)) {
      arena.setCharacter(Arena.CharacterType.HERO, attacker);
    }
    XSound.ENTITY_PLAYER_DEATH.play(victim.getLocation());
    victim.damage(100.0);


    userAttacker.adjustStatistic("KILLS", 1);
    if(Role.isRole(Role.MURDERER, userAttacker)) {
      userAttacker.adjustStatistic("LOCAL_KILLS", 1);
      arena.adjustContributorValue(Role.DETECTIVE, userAttacker, plugin.getRandom().nextInt(2));
      ArenaUtils.addScore(userAttacker, ArenaUtils.ScoreAction.KILL_PLAYER, 0);
    }

    VersionUtils.sendTitles(victim, new MessageBuilder("IN_GAME_DEATH_SCREEN").asKey().build(), null, 5, 40, 50);

    if(Role.isRole(Role.MURDERER, userVictim)) {
      ArenaUtils.addScore(userAttacker, ArenaUtils.ScoreAction.KILL_MURDERER, 0);
      arena.adjustContributorValue(Role.MURDERER, userAttacker, plugin.getRandom().nextInt(2));
    } else if(plugin.getConfigPreferences().getOption("BOW_KILL_DETECTIVE") && (Role.isRole(Role.ANY_DETECTIVE, userVictim) || Role.isRole(Role.INNOCENT, userVictim))) {
      if(Role.isRole(Role.MURDERER, userAttacker)) {
        VersionUtils.sendTitles(victim, null, new MessageBuilder("IN_GAME_MESSAGES_GAME_END_PLACEHOLDERS_MURDERER_KILLED_YOU").asKey().build(), 5, 40, 5);
      } else {
        VersionUtils.sendTitles(victim, null, new MessageBuilder("IN_GAME_MESSAGES_GAME_END_PLACEHOLDERS_INNOCENT_KILLED_YOU").asKey().build(), 5, 40, 5);
      }

      //if else, murderer killed, so don't kill him :)
      if(Role.isRole(Role.ANY_DETECTIVE, userAttacker) || Role.isRole(Role.INNOCENT, userAttacker)) {
        VersionUtils.sendSubTitle(attacker, new MessageBuilder("IN_GAME_MESSAGES_GAME_END_PLACEHOLDERS_INNOCENT_KILLED_WRONGLY").asKey().build(), 5, 40, 5);

        attacker.damage(100.0);
        ArenaUtils.addScore(userAttacker, ArenaUtils.ScoreAction.INNOCENT_KILL, 0);
        plugin.getRewardsHandler().performReward(attacker, plugin.getRewardsHandler().getRewardType("KILL_DETECTIVE"));
        if(Role.isRole(Role.ANY_DETECTIVE, userAttacker) && arena.lastAliveDetective()) {
          arena.setDetectiveDead(true);
          if(Role.isRole(Role.FAKE_DETECTIVE, userAttacker)) {
            arena.setCharacter(Arena.CharacterType.FAKE_DETECTIVE, null);
          }
          ArenaUtils.dropBowAndAnnounce(arena, victim);
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerDie(PlayerDeathEvent e) {
    Player player = e.getEntity();
    Arena arena = plugin.getArenaRegistry().getArena(player);
    if(arena == null) {
      return;
    }
    IUser user = plugin.getUserManager().getUser(player);
    ComplementAccessor.getComplement().setDeathMessage(e, "");
    e.getDrops().clear();
    e.setDroppedExp(0);
    plugin.getCorpseHandler().spawnCorpse(player, arena);
    XPotion.BLINDNESS.buildPotionEffect(3 * 20, 1).apply(player);
    if(arena.getArenaState() == IArenaState.STARTING) {
      return;
    } else if(arena.getArenaState() == IArenaState.ENDING || arena.getArenaState() == IArenaState.RESTARTING) {
      player.getInventory().clear();
      player.setFlying(false);
      player.setAllowFlight(false);
      user.setStatistic("LOCAL_GOLD", 0);
      return;
    }
    if(Role.isRole(Role.MURDERER, user, arena) && arena.lastAliveMurderer()) {
      ArenaUtils.onMurdererDeath(arena);
    }
    if(Role.isRole(Role.ANY_DETECTIVE, user) && arena.lastAliveDetective()) {
      arena.setDetectiveDead(true);
      if(Role.isRole(Role.FAKE_DETECTIVE, user)) {
        arena.setCharacter(Arena.CharacterType.FAKE_DETECTIVE, null);
      }
      ArenaUtils.dropBowAndAnnounce(arena, player);
    }
    user.adjustStatistic("DEATHS", 1);
    user.setSpectator(true);
    VersionUtils.setCollidable(player, false);
    player.setGameMode(GameMode.SURVIVAL);
    user.setStatistic("LOCAL_GOLD", 0);
    ArenaUtils.hidePlayer(player, arena);
    // After globally hiding, re-show spectators/dead players to each other
    ArenaUtils.showSpectatorsToEachOther(arena);
    // Apply gray glow so spectators/dead can recognize their state
    ArenaUtils.applySpectatorGlow(arena);
    // Apply half-scale (0.5) to spectators/dead if supported
    ArenaUtils.applySpectatorScale(arena);
    player.setAllowFlight(true);
    player.setFlying(true);
    player.getInventory().clear();
    if(plugin.getConfigPreferences().getOption("HIDE_DEATH")) {
      new MessageBuilder(MessageBuilder.ActionType.DEATH).player(player).arena(arena).sendArena();
    }

    if(arena.getArenaState() != IArenaState.ENDING && arena.getArenaState() != IArenaState.RESTARTING) {
      arena.addDeathPlayer(player);
    }
    //we must call it ticks later due to instant respawn bug
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      player.spigot().respawn();
      plugin.getSpecialItemManager().addSpecialItemsOfStage(player, SpecialItem.DisplayStage.SPECTATOR);
    }, 5);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onRespawn(PlayerRespawnEvent event) {
    Player player = event.getPlayer();
    Arena arena = plugin.getArenaRegistry().getArena(player);
    if(arena == null) {
      return;
    }
    if(arena.getArenaState() == IArenaState.STARTING || arena.getArenaState() == IArenaState.WAITING_FOR_PLAYERS) {
      event.setRespawnLocation(arena.getLobbyLocation());
      return;
    }
    if(arena.getArenaState() == IArenaState.RESTARTING) {
      event.setRespawnLocation(arena.getEndLocation());
      return;
    }
    if(arena.getPlayers().contains(player)) {
      IUser user = plugin.getUserManager().getUser(player);
      org.bukkit.Location firstSpawn = arena.getPlayerSpawnPoints().get(0);

      if(player.getLocation().getWorld().equals(firstSpawn.getWorld())) {
        event.setRespawnLocation(player.getLocation());
      } else {
        event.setRespawnLocation(firstSpawn);
      }
      player.setAllowFlight(true);
      player.setFlying(true);
      user.setSpectator(true);
      ArenaUtils.hidePlayer(player, arena);
      // After globally hiding, re-show spectators/dead players to each other
      ArenaUtils.showSpectatorsToEachOther(arena);
      // Apply gray glow so spectators/dead can recognize their state
      ArenaUtils.applySpectatorGlow(arena);
      // Apply half-scale (0.5) to spectators/dead if supported
      ArenaUtils.applySpectatorScale(arena);
      VersionUtils.setCollidable(player, false);
      player.setGameMode(GameMode.SURVIVAL);
      player.removePotionEffect(PotionEffectType.NIGHT_VISION);
      user.setStatistic("LOCAL_GOLD", 0);
      plugin.getRewardsHandler().performReward(player, plugin.getRewardsHandler().getRewardType("PLAYER_DEATH"));
    }
  }


  @EventHandler
  public void locatorDistanceUpdate(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    Arena arena = plugin.getArenaRegistry().getArena(player);
    if(arena == null) {
      return;
    }
    IUser user = plugin.getUserManager().getUser(player);
    //skip spectators
    if(user.isSpectator()) {
      return;
    }
    if(arena.getArenaState() == IArenaState.IN_GAME) {
      if(Role.isRole(Role.INNOCENT, user, arena)) {
        if(player.getInventory().getItem(ItemPosition.BOW_LOCATOR.getOtherRolesItemPosition()) != null) {
          ItemStack bowLocator = new ItemStack(Material.COMPASS, 1);
          ItemMeta bowMeta = bowLocator.getItemMeta();
          ComplementAccessor.getComplement().setDisplayName(bowMeta, new MessageBuilder("IN_GAME_MESSAGES_ARENA_LOCATOR_BOW").asKey().player(player).arena(arena).build() + " §7| §a" + (int) Math.round(player.getLocation().distance(player.getCompassTarget())));
          bowLocator.setItemMeta(bowMeta);
          ItemPosition.setItem(user, ItemPosition.BOW_LOCATOR, bowLocator);
          return;
        }
      }
      if(arena.isMurdererLocatorReceived() && Role.isRole(Role.MURDERER, user, arena) && arena.isMurderAlive(player)) {
        ItemStack innocentLocator = new ItemStack(Material.COMPASS, 1);
        ItemMeta innocentMeta = innocentLocator.getItemMeta();
        for(Player p : arena.getPlayersLeft()) {
          Arena playerArena = plugin.getArenaRegistry().getArena(p);
          IUser playerUser = plugin.getUserManager().getUser(p);

          if(Role.isRole(Role.INNOCENT, playerUser, playerArena) || Role.isRole(Role.ANY_DETECTIVE, playerUser, playerArena)) {
            ComplementAccessor.getComplement().setDisplayName(innocentMeta, new MessageBuilder("IN_GAME_MESSAGES_ARENA_LOCATOR_INNOCENT").asKey().player(player).arena(arena).build() + " §7| §a" + (int) Math.round(player.getLocation().distance(p.getLocation())));
            innocentLocator.setItemMeta(innocentMeta);
            ItemPosition.setItem(user, ItemPosition.INNOCENTS_LOCATOR, innocentLocator);
          }
        }
      }
    }
  }

  @EventHandler
  public void onDrop(PlayerDropItemEvent event) {
    if(plugin.getArenaRegistry().getArena(event.getPlayer()) != null && plugin.getArenaRegistry().getArena(event.getPlayer()).getArenaState() == IArenaState.IN_GAME) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onItemMove(InventoryClickEvent event) {
    if(event.getWhoClicked() instanceof Player && plugin.getArenaRegistry().isInArena((Player) event.getWhoClicked())) {
      if(XInventoryView.of(event.getView()).getType() == InventoryType.CRAFTING || XInventoryView.of(event.getView()).getType() == InventoryType.PLAYER) {
        event.setResult(Event.Result.DENY);
      }
    }
  }
}
