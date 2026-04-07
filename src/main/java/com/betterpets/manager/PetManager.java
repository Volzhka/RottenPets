package com.betterpets.manager;

import com.betterpets.BetterPetsPlugin;
import com.betterpets.data.PetData;
import com.betterpets.data.PetType;
import com.betterpets.data.PlayerData;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Gestor central de mascotas.
 * Maneja el ciclo de vida completo: spawn, despawn, seguimiento,
 * EXP/niveles, y coordinación con AbilityManager y StorageManager.
 */
public class PetManager {

    private final BetterPetsPlugin plugin;
    private final StorageManager storage;
    private final AbilityManager abilities;

    // Datos de jugadores cargados en memoria
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();
    // Entidad activa de mascota por jugador
    private final Map<UUID, Entity> activePetEntities = new HashMap<>();

    // Temporizadores por jugador (para habilidades periódicas)
    private final Map<UUID, Integer> chickenTimers  = new HashMap<>();
    private final Map<UUID, Integer> parrotTimers   = new HashMap<>();
    private final Map<UUID, Integer> reaperTimers   = new HashMap<>();

    public PetManager(BetterPetsPlugin plugin) {
        this.plugin = plugin;
        this.storage  = plugin.getStorageManager();
        this.abilities = plugin.getAbilityManager();
    }

    // =========================================================
    //  DATOS DE JUGADOR
    // =========================================================

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, storage::loadPlayerData);
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        // Solo carga desde disco si no está ya en memoria
        PlayerData data = playerDataMap.computeIfAbsent(uuid, storage::loadPlayerData);
        // Spawnear mascota activa (silencioso = sin mensaje de chat)
        if (data.getActivePet() != null && !hasPetEntity(player)) {
            spawnPetEntitySilent(player, data.getActivePet());
        }
    }

    public void unloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        despawnPetEntity(player);
        abilities.removeAllPassiveEffects(player);
        PlayerData data = playerDataMap.remove(uuid);
        if (data != null) storage.savePlayerData(data);
        // Limpiar timers
        chickenTimers.remove(uuid);
        parrotTimers.remove(uuid);
        reaperTimers.remove(uuid);
    }

    /** Guarda todos los datos sucios (llamado periódicamente). */
    public void saveAll() {
        for (PlayerData data : playerDataMap.values()) {
            storage.saveIfDirty(data);
        }
    }

    // =========================================================
    //  GESTIÓN DE MASCOTAS
    // =========================================================

    /**
     * Añade una mascota al inventario del jugador.
     * @return true si se pudo añadir.
     */
    public boolean givePet(Player player, PetType type) {
        PlayerData data = getPlayerData(player.getUniqueId());
        int maxPets = plugin.getConfig().getInt("max-mascotas", 36);
        if (maxPets > 0 && data.getPetCount() >= maxPets
                && !player.hasPermission("betterpets.bypass.limit")) {
            plugin.getMsgManager().send(player, "mascota.limite-alcanzado",
                "{0}", String.valueOf(maxPets));
            return false;
        }
        PetData pet = new PetData(type);
        data.addPet(pet);
        plugin.getMsgManager().send(player, "mascota.obtenida",
            "{0}", plugin.getPetsConfig().getString("mascotas." + type.getKey() + ".nombre", type.getKey()));
        return true;
    }

    /**
     * Activa una mascota del inventario del jugador (por índice).
     * Despawnea la mascota anterior y spawnea la nueva.
     */
    public void activatePet(Player player, int petIndex) {
        PlayerData data = getPlayerData(player.getUniqueId());
        PetData pet = data.getPet(petIndex);
        if (pet == null) return;

        // Si ya está activa, solo mostrar info
        if (data.getActivePetIndex() == petIndex) {
            plugin.getMsgManager().send(player, "mascota.ya-activa",
                "{0}", getPetDisplayName(pet.getType()));
            return;
        }

        // Quitar efectos de la mascota anterior
        despawnPetEntity(player);
        if (data.getActivePet() != null) {
            abilities.removeAllPassiveEffects(player);
        }

        data.setActivePetIndex(petIndex);
        spawnPetEntity(player, pet);

        plugin.getMsgManager().send(player, "mascota.cambiada",
            "{0}", getPetDisplayName(pet.getType()));
    }

    /**
     * Despawnea la mascota activa del jugador (entidad desaparece, sigue en lista).
     */
    public void despawnActivePet(Player player) {
        PlayerData data = getPlayerData(player.getUniqueId());
        if (data.getActivePet() == null) {
            plugin.getMsgManager().send(player, "mascota.sin-activa");
            return;
        }
        String nombre = getPetDisplayName(data.getActivePet().getType());
        despawnPetEntity(player);
        abilities.removeAllPassiveEffects(player);
        data.clearActivePet();
        plugin.getMsgManager().send(player, "mascota.despawneada", "{0}", nombre);
    }

    /**
     * Convierte la mascota activa de vuelta en ítem y la quita de la lista.
     * El jugador recibe el ítem en su inventario.
     */
    public void convertActivePet(Player player) {
        PlayerData data = getPlayerData(player.getUniqueId());
        PetData pet = data.getActivePet();
        if (pet == null) {
            plugin.getMsgManager().send(player, "mascota.sin-activa");
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            plugin.getMsgManager().send(player, "mascota.inventario-lleno");
            return;
        }

        String nombre = getPetDisplayName(pet.getType());
        despawnPetEntity(player);
        abilities.removeAllPassiveEffects(player);

        // Crear ítem de la mascota
        ItemStack item = createPetItem(pet);
        player.getInventory().addItem(item);

        int idx = data.getActivePetIndex();
        data.clearActivePet();
        data.removePet(idx);

        plugin.getMsgManager().send(player, "mascota.convertida", "{0}", nombre);
    }

    /**
     * Alterna la visibilidad de la mascota activa.
     */
    public void toggleVisibility(Player player) {
        PlayerData data = getPlayerData(player.getUniqueId());
        PetData pet = data.getActivePet();
        if (pet == null) {
            plugin.getMsgManager().send(player, "mascota.sin-activa");
            return;
        }
        pet.toggleVisible();
        Entity entity = activePetEntities.get(player.getUniqueId());
        if (entity instanceof LivingEntity le) {
            // Ocultar/mostrar usando invisibilidad de poción
            if (!pet.isVisible()) {
                le.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.INVISIBILITY,
                    Integer.MAX_VALUE, 0, false, false, false));
            } else {
                le.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
            }
        }
        if (pet.isVisible()) {
            plugin.getMsgManager().send(player, "mascota.visible");
        } else {
            plugin.getMsgManager().send(player, "mascota.oculta");
        }
        data.markDirty();
    }

    // =========================================================
    //  ENTIDADES DE MASCOTA
    // =========================================================

    private void spawnPetEntity(Player player, PetData pet) {
        Location loc = player.getLocation();
        EntityType etype = getEntityType(pet.getType());
        Entity entity = player.getWorld().spawnEntity(loc, etype);
        configurePetEntity(entity, player, pet);
        activePetEntities.put(player.getUniqueId(), entity);
        plugin.getMsgManager().send(player, "mascota.spawneada",
            "{0}", getPetDisplayName(pet.getType()));
    }

    /** Versión silenciosa (sin mensaje de chat): usada en login/respawn/cambio de mundo. */
    private void spawnPetEntitySilent(Player player, PetData pet) {
        Location loc = player.getLocation();
        EntityType etype = getEntityType(pet.getType());
        Entity entity = player.getWorld().spawnEntity(loc, etype);
        configurePetEntity(entity, player, pet);
        activePetEntities.put(player.getUniqueId(), entity);
    }

    private void configurePetEntity(Entity entity, Player owner, PetData pet) {
        boolean invulnerable = plugin.getConfig().getBoolean("mascota.invulnerable", true);
        boolean silent       = plugin.getConfig().getBoolean("mascota.silenciosas", false);
        boolean nameVisible  = plugin.getConfig().getBoolean("mascota.nombre-visible", true);

        entity.setInvulnerable(invulnerable);
        entity.setSilent(silent);
        entity.setPersistent(false); // No guardada en el chunk, managed por el plugin
        entity.setCustomNameVisible(nameVisible);
        entity.setCustomName(ChatColor.translateAlternateColorCodes('&',
            "&b" + getPetDisplayName(pet.getType()) + " &7[Niv " + pet.getLevel() + "]"));

        // Tags para identificación
        entity.addScoreboardTag("BetterPets_pet");
        entity.addScoreboardTag("BetterPets_owner_" + owner.getUniqueId().toString().replace("-", ""));

        if (entity instanceof LivingEntity le) {
            le.setRemoveWhenFarAway(false);
            le.setCanPickupItems(false);

            // Aplicar invisibilidad si la mascota está oculta
            if (!pet.isVisible()) {
                le.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.INVISIBILITY,
                    Integer.MAX_VALUE, 0, false, false, false));
            }

            // Configuraciones específicas por tipo
            if (le instanceof Ageable ageable) ageable.setBaby();
            if (le instanceof Wolf wolf) { wolf.setTamed(true); wolf.setOwner(owner); }
            if (le instanceof Cat cat)   { cat.setTamed(true);  cat.setOwner(owner); }
            if (le instanceof Parrot parrot) {
                parrot.setTamed(true); parrot.setOwner(owner);
                // Asignar color al loro (rojo para Loro Rojo, azul para Búho)
                if (pet.getType() == PetType.LORO_ROJO) parrot.setVariant(Parrot.Variant.RED);
                else if (pet.getType() == PetType.BUHO) parrot.setVariant(Parrot.Variant.CYAN);
            }
            if (le instanceof Rabbit rabbit) {
                if (pet.getType() == PetType.HAMSTER) rabbit.setRabbitType(Rabbit.Type.SALT_AND_PEPPER);
                else rabbit.setRabbitType(Rabbit.Type.WHITE);
            }
            if (le instanceof Zombie zombie) zombie.setBaby(true); // Herobrine como zombie bebé
            if (le instanceof Panda panda && pet.getType() == PetType.PANDA) {
                panda.setMainGene(Panda.Gene.PLAYFUL);
            }
            if (le instanceof SnowGolem snowGolem) snowGolem.setDerp(false);

            // Hacer que la entidad no ataque al jugador
            if (le instanceof Mob mob) {
                mob.setTarget(null);
            }
        }
    }

    public void despawnPetEntity(Player player) {
        Entity entity = activePetEntities.remove(player.getUniqueId());
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    /**
     * Actualiza el seguimiento de la mascota al jugador.
     * Llamado cada ticks-seguimiento desde PetTickTask.
     */
    public void tickFollowPet(Player player) {
        Entity entity = activePetEntities.get(player.getUniqueId());
        if (entity == null || !entity.isValid()) {
            // Respawnear si se perdió la entidad
            PlayerData data = getPlayerData(player.getUniqueId());
            if (data.getActivePet() != null) {
                spawnPetEntity(player, data.getActivePet());
            }
            return;
        }

        // Si el jugador cambió de mundo, teletransportar
        if (!entity.getWorld().equals(player.getWorld())) {
            entity.teleport(player.getLocation());
            return;
        }

        double distance = entity.getLocation().distance(player.getLocation());
        double teleportDist = plugin.getConfig().getDouble("mascota.distancia-teleport", 20.0);
        double stopDist     = plugin.getConfig().getDouble("mascota.distancia-parada", 3.0);

        // Si está muy lejos, teletransportar
        if (distance > teleportDist) {
            entity.teleport(getSafeLocationNear(player));
            return;
        }

        // Si está suficientemente cerca, no mover
        if (distance <= stopDist) return;

        boolean usePathfinder = !plugin.getConfig().getBoolean("rendimiento.usar-teleport-directo", false);

        if (usePathfinder && entity instanceof Mob mob) {
            double speed = plugin.getConfig().getDouble("mascota.velocidad-seguimiento", 1.4);
            mob.getPathfinder().moveTo(player, speed);
        } else {
            // Mover gradualmente hacia el jugador
            Location target = getSafeLocationNear(player);
            entity.teleport(target);
        }
    }

    /**
     * Tick de habilidades pasivas. Llamado cada ticks-habilidades desde PetTickTask.
     */
    public void tickAbilities(Player player) {
        PlayerData data = getPlayerData(player.getUniqueId());
        PetData pet = data.getActivePet();
        if (pet == null) return;

        abilities.applyPassiveAbility(player, pet);
        tickPeriodicAbilities(player, pet);
    }

    /** Habilidades que dependen de intervalos propios (gallina, loro, segador). */
    private void tickPeriodicAbilities(Player player, PetData pet) {
        UUID uuid = player.getUniqueId();
        int level = pet.getLevel();

        switch (pet.getType()) {
            case GALLINA -> {
                int interval = plugin.getPetsConfig().getInt("mascotas.gallina.intervalo-huevos", 60) * 20;
                int timer = chickenTimers.getOrDefault(uuid, 0) + 1;
                chickenTimers.put(uuid, timer);
                if (timer >= interval) {
                    chickenTimers.put(uuid, 0);
                    abilities.triggerChickenEgg(player);
                    plugin.getMsgManager().send(player, "habilidades.gallina-huevo");
                }
            }
            case LORO_ROJO -> {
                int interval = plugin.getPetsConfig().getInt("mascotas.loro_rojo.intervalo-objetos", 120) * 20;
                int timer = parrotTimers.getOrDefault(uuid, 0) + 1;
                parrotTimers.put(uuid, timer);
                if (timer >= interval) {
                    parrotTimers.put(uuid, 0);
                    abilities.triggerRedParrot(player);
                }
            }
            case SEGADOR -> {
                // Aura cada 3 segundos (60 ticks)
                int timer = reaperTimers.getOrDefault(uuid, 0) + 1;
                reaperTimers.put(uuid, timer);
                if (timer >= 60) {
                    reaperTimers.put(uuid, 0);
                    abilities.triggerReaperAura(player, level);
                }
            }
            default -> {} // Sin timer propio
        }
    }

    // =========================================================
    //  EXP Y NIVELES
    // =========================================================

    /**
     * Añade EXP a la mascota activa del jugador.
     * @param amount cantidad de EXP a añadir.
     */
    public void addExpToActivePet(Player player, int amount) {
        PlayerData data = getPlayerData(player.getUniqueId());
        PetData pet = data.getActivePet();
        if (pet == null) return;

        if (pet.getLevel() >= plugin.getConfig().getInt("exp.nivel-maximo", 100)) return;

        double mult = plugin.getConfig().getDouble("exp.multiplicador-exp-jugador", 1.0);
        long toAdd = Math.max(1, (long)(amount * mult));

        long base = plugin.getConfig().getLong("exp.base", 50);
        long multiplier = plugin.getConfig().getLong("exp.multiplicador", 50);
        int maxLevel = plugin.getConfig().getInt("exp.nivel-maximo", 100);

        int levelsGained = pet.addExp(toAdd, maxLevel, base, multiplier);
        data.markDirty();

        if (levelsGained > 0) {
            onPetLevelUp(player, pet, levelsGained);
            // Actualizar nombre de la entidad
            Entity entity = activePetEntities.get(player.getUniqueId());
            if (entity != null) {
                entity.setCustomName(ChatColor.translateAlternateColorCodes('&',
                    "&b" + getPetDisplayName(pet.getType()) + " &7[Niv " + pet.getLevel() + "]"));
            }
        }
    }

    private void onPetLevelUp(Player player, PetData pet, int levelsGained) {
        String nombre = getPetDisplayName(pet.getType());
        boolean isMax = pet.getLevel() >= plugin.getConfig().getInt("exp.nivel-maximo", 100);

        if (isMax) {
            plugin.getMsgManager().send(player, "nivel.maximo", "{0}", nombre);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        } else {
            plugin.getMsgManager().send(player, "nivel.subio",
                "{0}", nombre, "{1}", String.valueOf(pet.getLevel()));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        }

        // Efecto de partículas
        if (plugin.getConfig().getBoolean("exp.efecto-subida", true)) {
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);
        }
    }

    // =========================================================
    //  UTILIDADES
    // =========================================================

    public Entity getActivePetEntity(Player player) {
        return activePetEntities.get(player.getUniqueId());
    }

    public boolean hasPetEntity(Player player) {
        Entity e = activePetEntities.get(player.getUniqueId());
        return e != null && e.isValid();
    }

    /** Nombre de display de la mascota desde la config de pets.yml */
    public String getPetDisplayName(PetType type) {
        return plugin.getPetsConfig().getString(
            "mascotas." + type.getKey() + ".nombre", type.getKey());
    }

    /** Obtiene el EntityType configurado en pets.yml o usa el default del enum. */
    public EntityType getEntityType(PetType type) {
        String etName = plugin.getPetsConfig().getString(
            "mascotas." + type.getKey() + ".entidad");
        if (etName != null) {
            try { return EntityType.valueOf(etName); }
            catch (IllegalArgumentException ignored) {}
        }
        return type.getDefaultEntityType();
    }

    /**
     * Crea un ítem de mascota (libro encantado con metadatos).
     */
    public ItemStack createPetItem(PetData pet) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String nombre = getPetDisplayName(pet.getType());
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
            "&a[Niv " + pet.getLevel() + "] " + nombre));

        List<String> lore = new ArrayList<>();
        List<String> desc = plugin.getPetsConfig().getStringList(
            "mascotas." + pet.getType().getKey() + ".descripcion");
        for (String line : desc) {
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7" + line));
        }
        lore.add("");
        String habilidad = plugin.getPetsConfig().getString(
            "mascotas." + pet.getType().getKey() + ".habilidad", "");
        lore.add(ChatColor.translateAlternateColorCodes('&', "&6✧ " + habilidad));
        List<String> hDesc = plugin.getPetsConfig().getStringList(
            "mascotas." + pet.getType().getKey() + ".descripcion-habilidad");
        for (String line : hDesc) {
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7" + line));
        }
        lore.add("");
        lore.add(ChatColor.translateAlternateColorCodes('&',
            "&7Nivel: &b" + pet.getLevel() + " &7| EXP: &e" + pet.getExp()));

        // Guardar metadatos en el item usando PersistentDataContainer
        meta.setLore(lore);
        org.bukkit.NamespacedKey typeKey = new org.bukkit.NamespacedKey(plugin, "pet_type");
        org.bukkit.NamespacedKey levelKey = new org.bukkit.NamespacedKey(plugin, "pet_level");
        org.bukkit.NamespacedKey expKey   = new org.bukkit.NamespacedKey(plugin, "pet_exp");
        meta.getPersistentDataContainer().set(typeKey,  org.bukkit.persistence.PersistentDataType.STRING, pet.getType().getKey());
        meta.getPersistentDataContainer().set(levelKey, org.bukkit.persistence.PersistentDataType.INTEGER, pet.getLevel());
        meta.getPersistentDataContainer().set(expKey,   org.bukkit.persistence.PersistentDataType.LONG, pet.getExp());

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Lee un ítem de mascota y extrae su PetData (o null si no es un ítem de mascota).
     */
    public PetData readPetItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        org.bukkit.NamespacedKey typeKey = new org.bukkit.NamespacedKey(plugin, "pet_type");
        if (!meta.getPersistentDataContainer().has(typeKey)) return null;

        String typeStr = meta.getPersistentDataContainer().get(typeKey, org.bukkit.persistence.PersistentDataType.STRING);
        PetType type = PetType.fromKey(typeStr);
        if (type == null) return null;

        org.bukkit.NamespacedKey levelKey = new org.bukkit.NamespacedKey(plugin, "pet_level");
        org.bukkit.NamespacedKey expKey   = new org.bukkit.NamespacedKey(plugin, "pet_exp");
        int level = meta.getPersistentDataContainer().getOrDefault(levelKey, org.bukkit.persistence.PersistentDataType.INTEGER, 1);
        long exp  = meta.getPersistentDataContainer().getOrDefault(expKey,   org.bukkit.persistence.PersistentDataType.LONG, 0L);

        return new PetData(type, level, exp, true);
    }

    /** Crea el ítem de menú (cristal de mascotas). */
    public ItemStack createMenuKey() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6Cristal de Mascotas"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.translateAlternateColorCodes('&', "&7Haz clic para abrir el menú de mascotas."));
        meta.setLore(lore);
        org.bukkit.NamespacedKey menuKey = new org.bukkit.NamespacedKey(plugin, "pet_menu_key");
        meta.getPersistentDataContainer().set(menuKey, org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMenuKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        org.bukkit.NamespacedKey menuKey = new org.bukkit.NamespacedKey(plugin, "pet_menu_key");
        return meta.getPersistentDataContainer().has(menuKey);
    }

    private Location getSafeLocationNear(Player player) {
        // Posición ligeramente detrás del jugador
        Location loc = player.getLocation().clone();
        loc.subtract(player.getLocation().getDirection().normalize().multiply(1.5));
        return loc;
    }
}
