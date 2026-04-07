package com.betterpets.listener;

import com.betterpets.BetterPetsPlugin;
import com.betterpets.data.PetData;
import com.betterpets.data.PlayerData;
import com.betterpets.data.PetType;
import com.betterpets.manager.AbilityManager;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;

/**
 * Listener para eventos de entidades:
 * - Proteger entidades de mascota (invulnerabilidad, sin drops)
 * - Habilidades de trigger: Perro, Fénix, Segador, Pufferfish, Dragones, Guardián
 * - Daño del Warden a jugadores con Guardián
 */
public class EntityListener implements Listener {

    private final BetterPetsPlugin plugin;

    public EntityListener(BetterPetsPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================
    //  PROTECCIÓN DE MASCOTAS
    // =========================================================

    /** Impedir que las mascotas mueran. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPetDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!entity.getScoreboardTags().contains("BetterPets_pet")) return;
        event.setCancelled(true);
    }

    /** Las mascotas no pueden atacar a nadie. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPetAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager.getScoreboardTags().contains("BetterPets_pet")) {
            event.setCancelled(true);
        }
    }

    /** Las mascotas no dejan drops ni EXP. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPetDeath(EntityDeathEvent event) {
        if (event.getEntity().getScoreboardTags().contains("BetterPets_pet")) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    /** Mascotas no pueden ser blanco de mobs. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() != null
                && event.getTarget().getScoreboardTags().contains("BetterPets_pet")) {
            event.setCancelled(true);
        }
    }

    // =========================================================
    //  HABILIDADES DE TRIGGER
    // =========================================================

    /**
     * Evento principal de daño: activa habilidades trigger de mascotas.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        // El jugador RECIBE daño → activar habilidades defensivas
        if (!(event.getEntity() instanceof Player player)) return;

        PlayerData data = plugin.getPetManager().getPlayerData(player.getUniqueId());
        PetData pet = data.getActivePet();
        if (pet == null) return;

        Entity attacker = event.getDamager();
        int level = pet.getLevel();

        switch (pet.getType()) {
            case PERRO -> {
                if (plugin.getAbilityManager().triggerDogAbility(player, attacker, level)) {
                    plugin.getMsgManager().send(player, "habilidades.perro-marchitamiento");
                }
            }
            case PEZ_GLOBO -> {
                plugin.getAbilityManager().triggerPufferfishAlert(player, level);
                plugin.getMsgManager().send(player, "habilidades.pufferfish-alerta");
            }
            default -> {}
        }
    }

    /**
     * El jugador ATACA a algo → activar habilidades ofensivas.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAttacks(EntityDamageByEntityEvent event) {
        // El jugador es el atacante
        Entity damager = event.getDamager();
        Player player = null;

        if (damager instanceof Player p) {
            player = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            player = p;
        }

        if (player == null) return;

        PlayerData data = plugin.getPetManager().getPlayerData(player.getUniqueId());
        PetData pet = data.getActivePet();
        if (pet == null) return;

        Entity target = event.getEntity();
        int level = pet.getLevel();

        switch (pet.getType()) {
            case FENIX -> plugin.getAbilityManager().triggerPhoenixFire(player, target, level);
            case DRAGON_ROJO -> plugin.getAbilityManager().triggerRedDragon(player, target, level);
            case DRAGON_AZUL -> plugin.getAbilityManager().triggerBlueDragon(player, target, level);
            case SEGADOR -> plugin.getAbilityManager().triggerReaperHarvest(player, target, level);
            default -> {}
        }
    }

    /**
     * Guardián del Abismo: si el jugador tiene la mascota Guardián, cancelar su daño.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWardenDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Warden)) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (plugin.getAbilityManager().isWardenImmune(player)) {
            event.setCancelled(true);
            plugin.getMsgManager().send(player, "habilidades.guardian-inmunidad");
        }
    }

    /**
     * Sonic Boom del Guardián: si el jugador tiene la mascota Guardián, cancelar.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSonicBoom(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Warden)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.SONIC_BOOM) return;

        if (plugin.getAbilityManager().isWardenImmune(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Explosiones: si el jugador tiene la mascota Ghast con resistencia alta,
     * también cancelar el retroceso (el daño ya se reduce por atributo).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;

        PlayerData data = plugin.getPetManager().getPlayerData(player.getUniqueId());
        PetData pet = data.getActivePet();
        if (pet == null || pet.getType() != PetType.GHAST) return;

        // El atributo GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE reduce el retroceso.
        // El daño ya se reduce por armor/resistencia. Solo acción adicional si nivel es muy alto.
    }
}
