package com.betterpets.listener;

import com.betterpets.BetterPetsPlugin;
import com.betterpets.data.PetData;
import com.betterpets.data.PetType;
import com.betterpets.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

/**
 * Listener para eventos del jugador:
 * - Conexión/desconexión: cargar/guardar datos
 * - Muerte: manejar mascota activa
 * - Cambio de mundo: relocalizar mascota
 * - Ganancia de EXP: transferir a mascota activa
 * - Click derecho con cristal de mascotas: abrir menú
 * - Usar ítem de mascota: añadir al inventario
 */
public class PlayerListener implements Listener {

    private final BetterPetsPlugin plugin;

    public PlayerListener(BetterPetsPlugin plugin) {
        this.plugin = plugin;
    }

    // --- Conexión ---
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Cargar datos en el siguiente tick para evitar problemas de timing
        plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> plugin.getPetManager().loadPlayer(event.getPlayer()), 5L);
    }

    // --- Desconexión ---
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPetManager().unloadPlayer(event.getPlayer());
    }

    // --- Muerte del jugador ---
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerData data = plugin.getPetManager().getPlayerData(player.getUniqueId());
        // Despawnear mascota para evitar que quede suelta
        plugin.getPetManager().despawnPetEntity(player);
        plugin.getAbilityManager().removeAllPassiveEffects(player);
        // La mascota se vuelve a spawnear al respawnear si había una activa
    }

    // --- Respawn del jugador ---
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PlayerData data = plugin.getPetManager().getPlayerData(player.getUniqueId());
            if (data.getActivePet() != null) {
                // Solo spawnear la entidad sin mensaje
                plugin.getPetManager().despawnPetEntity(player); // limpieza previa
                plugin.getPetManager().loadPlayer(player);       // re-cargar para spawnear
            }
        }, 20L);
    }

    // --- Cambio de mundo ---
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.getConfig().getBoolean("mascota.seguir-dimensiones", true)) {
            // La entidad se teletransportará automáticamente en el siguiente tick de seguimiento
            // Solo necesitamos despawnear la entidad del mundo anterior
            plugin.getPetManager().despawnPetEntity(player);
            // Respawnear en el nuevo mundo en el siguiente tick
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                PlayerData data = plugin.getPetManager().getPlayerData(player.getUniqueId());
                if (data.getActivePet() != null) {
                    plugin.getPetManager().loadPlayer(player);
                }
            }, 5L);
        }
    }

    // --- Ganancia de EXP: transferir a mascota activa ---
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExpGain(PlayerExpChangeEvent event) {
        if (!plugin.getConfig().getBoolean("exp.ganar-exp-jugador", true)) return;
        int amount = event.getAmount();
        if (amount <= 0) return;
        plugin.getPetManager().addExpToActivePet(event.getPlayer(), amount);
    }

    // --- Interactuar con ítem: abrir menú si es el cristal ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        // Cristal de mascotas → abrir menú
        if (plugin.getPetManager().isMenuKey(item)) {
            event.setCancelled(true);
            if (player.hasPermission("betterpets.menu")) {
                plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.openPetMenu(player, 0));
            } else {
                plugin.getMsgManager().send(player, "general.sin-permiso");
            }
            return;
        }

        // Ítem de mascota en mano → añadir al inventario del jugador
        PetData petFromItem = plugin.getPetManager().readPetItem(item);
        if (petFromItem != null) {
            event.setCancelled(true);
            PlayerData data = plugin.getPetManager().getPlayerData(player.getUniqueId());
            int maxPets = plugin.getConfig().getInt("max-mascotas", 36);
            if (maxPets > 0 && data.getPetCount() >= maxPets
                    && !player.hasPermission("betterpets.bypass.limit")) {
                plugin.getMsgManager().send(player, "mascota.limite-alcanzado",
                    "{0}", String.valueOf(maxPets));
                return;
            }
            data.addPet(petFromItem);
            // Quitar el ítem de la mano
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().remove(item);
            }
            plugin.getMsgManager().send(player, "mascota.obtenida",
                "{0}", plugin.getPetManager().getPetDisplayName(petFromItem.getType()));
        }
    }
}
