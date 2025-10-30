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

package plugily.projects.murdermystery.arena.special;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import plugily.projects.minigamesbox.api.arena.IArenaState;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.helper.ItemBuilder;
import plugily.projects.minigamesbox.classic.utils.helper.ItemUtils;
import plugily.projects.minigamesbox.classic.utils.misc.complement.ComplementAccessor;
import plugily.projects.minigamesbox.classic.utils.version.ServerVersion;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XMaterial;
import plugily.projects.murdermystery.Main;
import plugily.projects.murdermystery.arena.Arena;
import plugily.projects.murdermystery.arena.special.mysterypotion.MysteryPotion;
import plugily.projects.murdermystery.arena.special.mysterypotion.MysteryPotionRegistry;
import plugily.projects.murdermystery.arena.special.pray.PrayerRegistry;
import plugily.projects.murdermystery.utils.ItemPosition;


/**
 * @author Plajer
 * <p>
 * Created at 16.10.2018
 */
public class SpecialBlockEvents implements Listener {

  private final Main plugin;

  public SpecialBlockEvents(Main plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
  }

  @EventHandler
  public void onSpecialBlockClick(PlayerInteractEvent event) {
    if(event.getClickedBlock() == null)
      return;

    if(ServerVersion.Version.isCurrentEqualOrHigher(ServerVersion.Version.v1_11) && event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
      return;
    }

    Arena arena = plugin.getArenaRegistry().getArena(event.getPlayer());
    if(arena == null) {
      return;
    }

    if(arena.getArenaState() != IArenaState.IN_GAME || plugin.getUserManager().getUser(event.getPlayer()).isSpectator()) {
      return;
    }

    // Check if player clicked a lever or button near a circuit breaker THAT WAS PAID FOR
    for(SpecialBlock specialBlock : arena.getSpecialBlocks()) {
      if(specialBlock.getSpecialBlockType() == SpecialBlock.SpecialBlockType.CIRCUIT_BREAKER) {
        // Only track if player paid for this breaker
        int playerClicks = arena.getCircuitBreakerPlayerClicks(specialBlock.getLocation(), event.getPlayer().getUniqueId());
        if (playerClicks > 0 && !arena.isCircuitBreakerActivated(specialBlock.getLocation())) {
          List<org.bukkit.block.Block> nearbyBlocks = plugin.getBukkitHelper().getNearbyBlocks(specialBlock.getLocation(), 3);
          if(nearbyBlocks.contains(event.getClickedBlock())) {
            Material clickedType = event.getClickedBlock().getType();
            if(clickedType == XMaterial.LEVER.parseMaterial() || clickedType.name().contains("BUTTON")) {
              arena.trackCircuitBreakerInteraction(specialBlock.getLocation(), event.getPlayer().getUniqueId());
              return;
            }
          }
        }
      }
    }

    // Check prayer lever (only near enchanting table)
    for(SpecialBlock specialBlock : arena.getSpecialBlocks()) {
      if(specialBlock.getSpecialBlockType() == SpecialBlock.SpecialBlockType.PRAISE_DEVELOPER) {
        if(event.getClickedBlock() != null && event.getClickedBlock().getType() == XMaterial.LEVER.parseMaterial() && plugin.getBukkitHelper().getNearbyBlocks(specialBlock.getLocation(), 3).contains(event.getClickedBlock())) {
          onPrayLeverClick(event);
          return;
        }
      }
    }

    // Handle direct special block clicks
    for(SpecialBlock specialBlock : arena.getSpecialBlocks()) {
      if(specialBlock.getLocation().getBlock().equals(event.getClickedBlock())) {
        switch(specialBlock.getSpecialBlockType()) {
          case MYSTERY_CAULDRON:
            onCauldronClick(event);
            return;
          case PRAISE_DEVELOPER:
            onPrayerClick(event);
            return;
          case CIRCUIT_BREAKER:
            onCircuitBreakerClick(event, specialBlock);
            return;
          case HORSE_PURCHASE:
          case RAPID_TELEPORTATION:
            //not yet implemented
          default:
            break;
        }
      }
    }
  }

  private void onCauldronClick(PlayerInteractEvent event) {
    if(event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.CAULDRON) {
      return;
    }

    if(event.getPlayer().getInventory().getItem(/* same for all roles */ ItemPosition.POTION.getOtherRolesItemPosition()) != null) {
      new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_SPECIAL_BLOCKS_CAULDRON_POTION").asKey().player(event.getPlayer()).sendPlayer();
      return;
    }

    IUser user = plugin.getUserManager().getUser(event.getPlayer());

    int localGold = user.getStatistic("LOCAL_GOLD");
    if(localGold < 1) {
      new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_SPECIAL_BLOCKS_NOT_ENOUGH_GOLD").asKey().player(event.getPlayer()).integer(1).sendPlayer();
      return;
    }

    org.bukkit.Location blockLoc = event.getClickedBlock().getLocation();

    VersionUtils.sendParticles("FIREWORKS_SPARK", event.getPlayer(), blockLoc, 10);
    Item item = blockLoc.getWorld().dropItemNaturally(blockLoc.clone().add(0, 1, 0), new ItemStack(Material.POTION, 1));
    item.setPickupDelay(10000);
    Bukkit.getScheduler().runTaskLater(plugin, item::remove, 20);
    user.adjustStatistic("LOCAL_GOLD", -1);
    ItemPosition.removeItem(user, new ItemStack(Material.GOLD_INGOT, 1));
    ItemPosition.setItem(user, ItemPosition.POTION, new ItemBuilder(XMaterial.POTION.parseItem()).name(MysteryPotionRegistry.getRandomPotion().getName()).build());
  }

  private void onPrayerClick(PlayerInteractEvent event) {
    if(event.getClickedBlock() == null || event.getClickedBlock().getType() != XMaterial.ENCHANTING_TABLE.parseMaterial()) {
      return;
    }

    event.setCancelled(true);

    IUser user = plugin.getUserManager().getUser(event.getPlayer());
    int localGold = user.getStatistic("LOCAL_GOLD");

    if(localGold < 1) {
      new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_SPECIAL_BLOCKS_NOT_ENOUGH_GOLD").asKey().player(event.getPlayer()).integer(1).sendPlayer();
      return;
    }
    new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_SPECIAL_BLOCKS_PRAY_CHAT").asKey().player(event.getPlayer()).sendPlayer();
    user.adjustStatistic("LOCAL_PRAISES", 1);
    VersionUtils.sendParticles("FIREWORKS_SPARK", event.getPlayer(), event.getClickedBlock().getLocation(), 10);
    user.adjustStatistic("LOCAL_GOLD", -1);
    ItemPosition.removeItem(user, new ItemStack(Material.GOLD_INGOT, 1));
  }

  private void onPrayLeverClick(PlayerInteractEvent event) {
    IUser user = plugin.getUserManager().getUser(event.getPlayer());
    if(user.getStatistic("LOCAL_PRAISES") < 1) {
      new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_SPECIAL_BLOCKS_PRAY_PAY").asKey().player(event.getPlayer()).sendPlayer();
      return;
    }
    PrayerRegistry.applyRandomPrayer(user);
    user.setStatistic("LOCAL_PRAISES", 0);
  }

  @EventHandler
  public void onMysteryPotionDrink(PlayerItemConsumeEvent event) {
    ItemStack item = event.getItem();
    if(item.getType() != XMaterial.POTION.parseMaterial() || !ItemUtils.isItemStackNamed(item)) {
      return;
    }

    if(plugin.getArenaRegistry().getArena(event.getPlayer()) == null) {
      return;
    }

    String itemDisplayName = ComplementAccessor.getComplement().getDisplayName(item.getItemMeta());
    IUser user = plugin.getUserManager().getUser(event.getPlayer());
    for(MysteryPotion potion : MysteryPotionRegistry.getMysteryPotions()) {
      if(itemDisplayName.equals(potion.getName())) {
        event.setCancelled(true);
        event.getPlayer().sendMessage(potion.getSubtitle());
        VersionUtils.sendTitles(event.getPlayer(), "", potion.getSubtitle(), 5, 40, 5);
        ItemPosition.setItem(user, ItemPosition.POTION, null);
        event.getPlayer().addPotionEffect(potion.getPotionEffect());
        return;
      }
    }
  }

  private void onCircuitBreakerClick(PlayerInteractEvent event, SpecialBlock specialBlock) {
    // Cancel the event FIRST to prevent opening inventory
    event.setCancelled(true);
    
    // Accept GLASS only
    if(event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.GLASS) {
      return;
    }

    Arena arena = plugin.getArenaRegistry().getArena(event.getPlayer());
    if(arena == null) {
      return;
    }
    
    IUser user = plugin.getUserManager().getUser(event.getPlayer());
    if(user.isSpectator()) {
      return;
    }

    if (!arena.isSabotageActive()) {
      new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_CIRCUIT_BREAKER_NO_SABOTAGE")
        .asKey()
        .player(event.getPlayer())
        .arena(arena)
        .sendPlayer();
      return;
    }

    // Check if already activated
    if(arena.isCircuitBreakerActivated(specialBlock.getLocation())) {
      new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_CIRCUIT_BREAKER_ALREADY_ACTIVATED")
        .asKey()
        .player(event.getPlayer())
        .arena(arena)
        .sendPlayer();
      return;
    }

    // Check if player has glowstone dust in hand
    ItemStack itemInHand = event.getPlayer().getInventory().getItemInMainHand();
    int requiredAmount = plugin.getConfig().getInt("Gold.Sabotage.Restoration.Circuit-Breakers.Glowstone-Dust.Required-Amount", 1);
    int currentAmount = arena.getCircuitBreakerGlowstoneCount(specialBlock.getLocation());
    int remainingAmount = arena.getCircuitBreakerGlowstoneRemaining(specialBlock.getLocation());
    
    if(itemInHand == null || itemInHand.getType() != Material.GLOWSTONE_DUST || itemInHand.getAmount() < 1) {
      // Show how many glowstone dust are still needed (global counter)
      new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_CIRCUIT_BREAKER_NO_GLOWSTONE")
        .asKey()
        .player(event.getPlayer())
        .arena(arena)
        .integer(remainingAmount)
        .sendPlayer();
      return;
    }

    // Consume 1 glowstone dust and add to global counter
    itemInHand.setAmount(itemInHand.getAmount() - 1);
    arena.addGlowstoneToCircuitBreaker(specialBlock.getLocation(), 1);
    
    // Check if enough glowstone has been deposited
    if(arena.getCircuitBreakerGlowstoneRemaining(specialBlock.getLocation()) > 0) {
      // Still need more glowstone
      int stillNeeded = arena.getCircuitBreakerGlowstoneRemaining(specialBlock.getLocation());
      new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_CIRCUIT_BREAKER_PROGRESS")
        .asKey()
        .player(event.getPlayer())
        .arena(arena)
        .integer(stillNeeded)
        .sendPlayer();
      return;
    }

    // Payment complete! Mark as "paid" - initialize with 1 click (meaning payment done)
    arena.getCircuitBreakerInteractions().computeIfAbsent(specialBlock.getLocation(), k -> new java.util.HashMap<>()).put(event.getPlayer().getUniqueId(), 1);
    
    // Send message to interact with components
    new MessageBuilder("IN_GAME_MESSAGES_ARENA_PLAYING_CIRCUIT_BREAKER_INCOMPLETE_STRUCTURE")
      .asKey()
      .player(event.getPlayer())
      .arena(arena)
      .sendPlayer();
  }

}

