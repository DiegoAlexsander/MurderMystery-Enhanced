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
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import plugily.projects.minigamesbox.api.arena.IArenaState;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.arena.PluginArena;
import plugily.projects.minigamesbox.classic.arena.managers.PluginMapRestorerManager;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.hologram.ArmorStandHologram;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.murdermystery.HookManager;
import plugily.projects.murdermystery.Main;
import plugily.projects.murdermystery.arena.corpse.Corpse;
import plugily.projects.murdermystery.arena.corpse.Stand;
import plugily.projects.murdermystery.arena.managers.MapRestorerManager;
import plugily.projects.murdermystery.arena.managers.ScoreboardManager;
import plugily.projects.murdermystery.arena.role.Role;
import plugily.projects.murdermystery.arena.special.SpecialBlock;
import plugily.projects.murdermystery.arena.states.InGameState;
import plugily.projects.murdermystery.arena.states.RestartingState;
import plugily.projects.murdermystery.arena.states.StartingState;
import plugily.projects.murdermystery.arena.states.WaitingState;

import java.util.*;

/**
 * @author Tigerpanzer_02
 * <p>
 * Created at 17.12.2021
 */
public class Arena extends PluginArena {

  private static Main plugin;

  private final List<Player> spectators = new ArrayList<>();
  private final List<Player> deaths = new ArrayList<>();
  private final List<Player> detectives = new ArrayList<>();
  private final List<Player> murderers = new ArrayList<>();
  private final List<Item> goldSpawned = new ArrayList<>();
  private final List<Item> glowstoneDustSpawned = new ArrayList<>();
  private final List<Corpse> corpses = new ArrayList<>();
  private final List<Stand> stands = new ArrayList<>();
  private final List<SpecialBlock> specialBlocks = new ArrayList<>();
  private List<Location> goldSpawnPoints = new ArrayList<>();
  private List<Location> playerSpawnPoints = new ArrayList<>();
  private int spawnGoldTimer = 0;
  private int spawnGoldTime = 0;
  private int spawnGlowstoneDustTimer = 0;
  private boolean detectiveDead;
  private boolean murdererLocatorReceived;
  private boolean hideChances;
  private boolean goldVisuals = false;
  
  // Permanent sabotage system fields
  private boolean sabotageActive = false;
  private int globalGoldCollected = 0;
  private final Set<Location> activatedCircuitBreakers = new HashSet<>();
  private final Map<Location, Map<UUID, Integer>> circuitBreakerInteractions = new HashMap<>(); // tracks click count per player
  private final Map<Location, Integer> circuitBreakerGlowstoneCount = new HashMap<>(); // tracks global glowstone dust deposited per breaker
  private BukkitTask glowstoneDustSpawnTask;
  
  private final Map<CharacterType, Player> gameCharacters = new EnumMap<>(CharacterType.class);
  private final MapRestorerManager mapRestorerManager;
  private ArmorStandHologram bowHologram;

  public Arena(String id) {
    super(id);
    setPluginValues();
    setScoreboardManager(new ScoreboardManager(this));
    mapRestorerManager = new MapRestorerManager(this);
    setMapRestorerManager(mapRestorerManager);
    addGameStateHandler(IArenaState.IN_GAME, new InGameState());
    addGameStateHandler(IArenaState.RESTARTING, new RestartingState());
    addGameStateHandler(IArenaState.STARTING, new StartingState());
    addGameStateHandler(IArenaState.WAITING_FOR_PLAYERS, new WaitingState());
  }

  public static void init(Main plugin) {
    Arena.plugin = plugin;
  }

  @Override
  public Main getPlugin() {
    return plugin;
  }


  @Override
  public PluginMapRestorerManager getMapRestorerManager() {
    return mapRestorerManager;
  }


  private void setPluginValues() {
  }

  public void addCorpse(Corpse corpse) {
    if(plugin.getHookManager().isFeatureEnabled(HookManager.HookFeature.CORPSES)) {
      corpses.add(corpse);
    }
  }

  public List<Corpse> getCorpses() {
    return corpses;
  }

  public List<Stand> getStands() {
    return stands;
  }

  public void addHead(Stand stand) {
    stands.add(stand);
  }

  public void setSpawnGoldTime(int spawnGoldTime) {
    this.spawnGoldTime = spawnGoldTime;
  }

  public void setHideChances(boolean hideChances) {
    this.hideChances = hideChances;
  }

  public boolean isDetectiveDead() {
    return detectiveDead;
  }

  public void setDetectiveDead(boolean detectiveDead) {
    this.detectiveDead = detectiveDead;
  }

  public boolean isMurdererLocatorReceived() {
    return murdererLocatorReceived;
  }

  public void setMurdererLocatorReceived(boolean murdererLocatorReceived) {
    this.murdererLocatorReceived = murdererLocatorReceived;
  }

  public Map<CharacterType, Player> getGameCharacters() {
    return gameCharacters;
  }

  public boolean isHideChances() {
    return hideChances;
  }

  @NotNull
  public List<Item> getGoldSpawned() {
    return goldSpawned;
  }

  @NotNull
  public List<Location> getGoldSpawnPoints() {
    return goldSpawnPoints;
  }

  public void setGoldSpawnPoints(@NotNull List<Location> goldSpawnPoints) {
    this.goldSpawnPoints = goldSpawnPoints;
  }

  private BukkitTask visualTask;

  public void startGoldVisuals() {
    if(visualTask != null) {
      return;
    }
    visualTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
      if(!goldVisuals || !plugin.isEnabled() || goldSpawnPoints.isEmpty() || getArenaState() != IArenaState.WAITING_FOR_PLAYERS) {
        //we need to cancel it that way as the arena class is an task
        visualTask.cancel();
        return;
      }
      for(Location goldLocations : goldSpawnPoints) {
        Location goldLocation = goldLocations.clone();
        goldLocation.add(0, 0.4, 0);
        Bukkit.getOnlinePlayers().forEach(player -> VersionUtils.sendParticles("REDSTONE", player, goldLocation, 10));
      }
    }, 20L, 20L);
  }

  public boolean isGoldVisuals() {
    return goldVisuals;
  }

  public void setGoldVisuals(boolean goldVisuals) {
    this.goldVisuals = goldVisuals;
    if(goldVisuals) {
      startGoldVisuals();
    }
  }

  public void loadSpecialBlock(SpecialBlock block) {
    if(!specialBlocks.contains(block)) {
      specialBlocks.add(block);
    }

    switch(block.getSpecialBlockType()) {
      case MYSTERY_CAULDRON:
        block.setArmorStandHologram(new ArmorStandHologram(plugin.getBukkitHelper().getBlockCenter(block.getLocation()), new MessageBuilder(plugin.getLanguageManager().getLanguageMessage("In-Game.Messages.Arena.Playing.Special-Blocks.Cauldron.Hologram")).build()));
        break;
      case PRAISE_DEVELOPER:
        ArmorStandHologram prayer = new ArmorStandHologram(plugin.getBukkitHelper().getBlockCenter(block.getLocation()));
        for(String str : plugin.getLanguageManager().getLanguageMessage("In-Game.Messages.Arena.Playing.Special-Blocks.Pray.Hologram").split(";")) {
          prayer.appendLine(new MessageBuilder(str).build());
        }
        block.setArmorStandHologram(prayer);
        break;
      case CIRCUIT_BREAKER:
        ArmorStandHologram circuitBreakerHolo = new ArmorStandHologram(plugin.getBukkitHelper().getBlockCenter(block.getLocation()));
        int requiredGlowstone = plugin.getConfig().getInt("Gold.Sabotage.Restoration.Circuit-Breakers.Glowstone-Dust.Required-Amount", 1);
        for(String str : plugin.getLanguageManager().getLanguageMessage("In-Game.Messages.Arena.Playing.Circuit-Breaker.Hologram").split(";")) {
          circuitBreakerHolo.appendLine(new MessageBuilder(str).integer(requiredGlowstone).build());
        }
        block.setArmorStandHologram(circuitBreakerHolo);
        break;
      case HORSE_PURCHASE:
      case RAPID_TELEPORTATION:
        //not yet implemented
      default:
        break;
    }
  }

  public List<SpecialBlock> getSpecialBlocks() {
    return specialBlocks;
  }

  public int getTotalRoleChances(Role role) {
    int totalRoleChances = 0;

    for(Player p : getPlayersLeft()) {
      IUser user = getPlugin().getUserManager().getUser(p);
      totalRoleChances += getContributorValue(role, user);
    }
    //avoid division / 0
    return totalRoleChances == 0 ? 1 : totalRoleChances;
  }

  public boolean isCharacterSet(Arena.CharacterType type) {
    return gameCharacters.containsKey(type);
  }

  public void setCharacter(Arena.CharacterType type, Player player) {
    gameCharacters.put(type, player);
  }

  public void setCharacter(Role role, Player player) {
    gameCharacters.put(role == Role.MURDERER ? CharacterType.MURDERER : CharacterType.DETECTIVE, player);
  }

  @Nullable
  public Player getCharacter(Arena.CharacterType type) {
    return gameCharacters.get(type);
  }

  public void addToDetectiveList(Player player) {
    detectives.add(player);
  }

  public boolean lastAliveDetective() {
    return aliveDetective() <= 1;
  }

  public int aliveDetective() {
    int alive = 0;
    for(Player player : getPlayersLeft()) {
      if(Role.isRole(Role.ANY_DETECTIVE, plugin.getUserManager().getUser(player), this) && isDetectiveAlive(player)) {
        alive++;
      }
    }
    return alive;
  }

  public boolean isDetectiveAlive(Player player) {
    for(Player p : getPlayersLeft()) {
      if(p == player && detectives.contains(p)) {
        return true;
      }
    }
    return false;
  }

  public List<Player> getDetectiveList() {
    return detectives;
  }

  public void addToMurdererList(Player player) {
    murderers.add(player);
  }

  public void removeFromMurdererList(Player player) {
    murderers.remove(player);
  }


  public boolean lastAliveMurderer() {
    return aliveMurderer() == 1;
  }

  public int aliveMurderer() {
    int alive = 0;
    for(Player player : getPlayersLeft()) {
      if(Role.isRole(Role.MURDERER, plugin.getUserManager().getUser(player), this) && isMurderAlive(player)) {
        alive++;
      }
    }
    return alive;
  }

  public boolean isMurderAlive(Player player) {
    for(Player p : getPlayersLeft()) {
      if(p == player && murderers.contains(p)) {
        return true;
      }
    }
    return false;
  }

  public List<Player> getMurdererList() {
    return murderers;
  }

  public void setBowHologram(ArmorStandHologram bowHologram) {
    if(bowHologram == null) {
      this.bowHologram = null;
      return;
    }

    this.bowHologram = bowHologram;
  }

  public ArmorStandHologram getBowHologram() {
    return bowHologram;
  }

  public void addDeathPlayer(Player player) {
    deaths.add(player);
  }

  public void removeDeathPlayer(Player player) {
    deaths.remove(player);
  }

  public boolean isDeathPlayer(Player player) {
    return deaths.contains(player);
  }

  public List<Player> getDeaths() {
    return deaths;
  }

  public void addSpectatorPlayer(Player player) {
    spectators.add(player);
  }

  public void removeSpectatorPlayer(Player player) {
    spectators.remove(player);
  }

  public boolean isSpectatorPlayer(Player player) {
    return spectators.contains(player);
  }

  public List<Location> getPlayerSpawnPoints() {
    return playerSpawnPoints;
  }

  public int getSpawnGoldTime() {
    return spawnGoldTime;
  }

  public int getSpawnGoldTimer() {
    return spawnGoldTimer;
  }

  public void setSpawnGoldTimer(int spawnGoldTimer) {
    this.spawnGoldTimer = spawnGoldTimer;
  }


  public void setPlayerSpawnPoints(@NotNull List<Location> playerSpawnPoints) {
    this.playerSpawnPoints = playerSpawnPoints;
  }

  public void adjustContributorValue(Role role, IUser user, int number) {
    user.adjustStatistic("CONTRIBUTION_" + role.name(), number);
  }

  private final Map<IUser, Integer> murdererContributions = new HashMap<>();
  private final Map<IUser, Integer> detectiveContributions = new HashMap<>();

  public Map<IUser, Integer> getMurdererContributions() {
    return murdererContributions;
  }

  public Map<IUser, Integer> getDetectiveContributions() {
    return detectiveContributions;
  }

  public int getContributorValue(Role role, IUser user) {
    if(role == Role.MURDERER && murdererContributions.containsKey(user)) {
      return murdererContributions.get(user);
    } else if(detectiveContributions.containsKey(user)) {
      return detectiveContributions.get(user);
    }
    Player player = user.getPlayer();
    int contributor = user.getStatistic("CONTRIBUTION_" + role.name());
    int increase = plugin.getPermissionsManager().getPermissionCategoryValue(role.name() + "_BOOSTER", player);
    int multiplicator = plugin.getPermissionsManager().getPermissionCategoryValue("CHANCES_BOOSTER", player);
    int calculatedContributor = (contributor + increase) * (multiplicator == 0 ? 1 :multiplicator);
    if(role == Role.MURDERER) {
      murdererContributions.put(user, calculatedContributor);
    } else {
      detectiveContributions.put(user, calculatedContributor);
    }
    return calculatedContributor;
  }

  public void resetContributorValue(Role role, IUser user) {
    user.setStatistic("CONTRIBUTION_" + role.name(), 1);
  }

  // Permanent sabotage system methods
  
  public boolean isSabotageActive() {
    return sabotageActive;
  }

  public void setSabotageActive(boolean active) {
    this.sabotageActive = active;
    if (!active) {
      // Reset sabotage state when deactivated
      globalGoldCollected = 0;
      activatedCircuitBreakers.clear();
      circuitBreakerInteractions.clear();
      circuitBreakerGlowstoneCount.clear(); // Reset glowstone counters
      
      // Remove glowstone dust from ground
      glowstoneDustSpawned.stream().filter(java.util.Objects::nonNull).forEach(org.bukkit.entity.Item::remove);
      glowstoneDustSpawned.clear();
      
      // Remove glowstone dust from player inventories
      for(org.bukkit.entity.Player player : getPlayers()) {
        player.getInventory().remove(org.bukkit.Material.GLOWSTONE_DUST);
      }
      
      if (glowstoneDustSpawnTask != null) {
        glowstoneDustSpawnTask.cancel();
        glowstoneDustSpawnTask = null;
      }
    }
  }

  public int getGlobalGoldCollected() {
    return globalGoldCollected;
  }

  public void addGlobalGoldCollected(int amount) {
    this.globalGoldCollected += amount;
  }

  public void resetGlobalGoldCollected() {
    this.globalGoldCollected = 0;
  }

  public Set<Location> getActivatedCircuitBreakers() {
    return activatedCircuitBreakers;
  }

  public void activateCircuitBreaker(Location location) {
    activatedCircuitBreakers.add(location);
  }

  public boolean isCircuitBreakerActivated(Location location) {
    return activatedCircuitBreakers.contains(location);
  }
  
  public void trackCircuitBreakerInteraction(Location location, UUID playerId) {
    Map<UUID, Integer> playerClicks = circuitBreakerInteractions.computeIfAbsent(location, k -> new HashMap<>());
    playerClicks.put(playerId, playerClicks.getOrDefault(playerId, 0) + 1);
    
    // Check if player completed all interactions (4 total: 1 payment + 3 clicks on lever/buttons)
    if (playerClicks.get(playerId) >= 4) {
      // Activate circuit breaker automatically
      activateCircuitBreaker(location);
      playerClicks.remove(playerId);
      
      // Send activation message
      org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(playerId);
      if (player != null) {
        new plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_CIRCUIT_BREAKER_ACTIVATED")
          .asKey()
          .player(player)
          .arena(this)
          .integer(activatedCircuitBreakers.size())
          .value(String.valueOf(getCircuitBreakerCount()))
          .sendArena();
        
        // Check if restoration should occur
        checkCircuitBreakerRestoration();
      }
    }
  }
  
  private void checkCircuitBreakerRestoration() {
    String restorationSystem = getPlugin().getConfig().getString("Gold.Sabotage.Restoration.System", "GOLD_COLLECTION").toUpperCase();
    int totalBreakers = getCircuitBreakerCount();
    int activatedBreakers = activatedCircuitBreakers.size();

    if(activatedBreakers >= totalBreakers && totalBreakers > 0) {
      boolean canRestore = true;

      // If BOTH system, also check gold collection
      if(restorationSystem.equals("BOTH")) {
        int requiredGold = getPlugin().getConfig().getInt("Gold.Sabotage.Restoration.Gold-Collection.Amount", 50);
        canRestore = (globalGoldCollected >= requiredGold);
      }

      if(canRestore || restorationSystem.equals("CIRCUIT_BREAKERS")) {
        // Call restoreLights through a scheduled task to avoid threading issues
        org.bukkit.Bukkit.getScheduler().runTask(getPlugin(), () -> {
          // Remove effects from all players
          for(org.bukkit.entity.Player p : getPlayers()) {
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
            if (plugily.projects.minigamesbox.classic.utils.version.ServerVersion.Version.isCurrentEqualOrHigher(plugily.projects.minigamesbox.classic.utils.version.ServerVersion.Version.v1_19)) {
              try {
                p.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS);
              } catch (Throwable ignored) {}
            }
          }
          
          // Deactivate sabotage (this will also clear glowstone dust)
          setSabotageActive(false);
          
          // Broadcast restoration success
          for(org.bukkit.entity.Player p : getPlayers()) {
            new plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_SABOTAGE_LIGHTS_RESTORED_BROADCAST")
              .asKey()
              .player(p)
              .arena(this)
              .sendPlayer();
          }
        });
      }
    }
  }
  
  public int getCircuitBreakerPlayerClicks(Location location, UUID playerId) {
    Map<UUID, Integer> playerClicks = circuitBreakerInteractions.get(location);
    if (playerClicks == null) {
      return 0;
    }
    return playerClicks.getOrDefault(playerId, 0);
  }
  
  public Map<Location, Map<UUID, Integer>> getCircuitBreakerInteractions() {
    return circuitBreakerInteractions;
  }
  
  public void clearCircuitBreakerInteraction(Location location, UUID playerId) {
    Map<UUID, Integer> playerClicks = circuitBreakerInteractions.get(location);
    if (playerClicks != null) {
      playerClicks.remove(playerId);
    }
  }

  public int getCircuitBreakerCount() {
    return (int) specialBlocks.stream()
        .filter(block -> block.getSpecialBlockType() == SpecialBlock.SpecialBlockType.CIRCUIT_BREAKER)
        .filter(block -> isCircuitBreakerValid(block))
        .count();
  }
  
  private boolean isCircuitBreakerValid(SpecialBlock block) {
    // Check if circuit breaker has required structure (1 lever + 2 buttons)
    List<org.bukkit.block.Block> nearbyBlocks = plugin.getBukkitHelper().getNearbyBlocks(block.getLocation(), 3);
    int leverCount = 0;
    int buttonCount = 0;
    
    for(org.bukkit.block.Block nearbyBlock : nearbyBlocks) {
      if(nearbyBlock.getType().name().equals("LEVER")) {
        leverCount++;
      }
      if(nearbyBlock.getType().name().contains("BUTTON")) {
        buttonCount++;
      }
    }
    
    return leverCount >= 1 && buttonCount >= 2;
  }

  @NotNull
  public List<Item> getGlowstoneDustSpawned() {
    return glowstoneDustSpawned;
  }

  public int getSpawnGlowstoneDustTimer() {
    return spawnGlowstoneDustTimer;
  }

  public void setSpawnGlowstoneDustTimer(int timer) {
    this.spawnGlowstoneDustTimer = timer;
  }

  public BukkitTask getGlowstoneDustSpawnTask() {
    return glowstoneDustSpawnTask;
  }

  public void setGlowstoneDustSpawnTask(BukkitTask task) {
    this.glowstoneDustSpawnTask = task;
  }

  /**
   * Adds glowstone dust to a circuit breaker's counter (global per breaker)
   */
  public void addGlowstoneToCircuitBreaker(Location breaker, int amount) {
    circuitBreakerGlowstoneCount.put(breaker, circuitBreakerGlowstoneCount.getOrDefault(breaker, 0) + amount);
  }

  /**
   * Gets how many glowstone dust have been deposited to this circuit breaker
   */
  public int getCircuitBreakerGlowstoneCount(Location breaker) {
    return circuitBreakerGlowstoneCount.getOrDefault(breaker, 0);
  }

  /**
   * Gets how many more glowstone dust are needed to activate this circuit breaker
   */
  public int getCircuitBreakerGlowstoneRemaining(Location breaker) {
    int required = getPlugin().getConfig().getInt("Gold.Sabotage.Restoration.Circuit-Breakers.Glowstone-Dust.Required-Amount", 1);
    int current = getCircuitBreakerGlowstoneCount(breaker);
    return Math.max(0, required - current);
  }

  /**
   * Resets the glowstone counter for a circuit breaker
   */
  public void resetCircuitBreakerGlowstone(Location breaker) {
    circuitBreakerGlowstoneCount.remove(breaker);
  }

  public enum CharacterType {
    MURDERER, DETECTIVE, FAKE_DETECTIVE, HERO
  }
}