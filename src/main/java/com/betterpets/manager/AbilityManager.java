package com.betterpets.manager;

import com.betterpets.BetterPetsPlugin;
import com.betterpets.data.PetData;
import com.betterpets.data.PetType;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Gestiona la aplicación y remoción de habilidades de mascotas.
 * Pasivas: se aplican en cada tick de habilidades.
 * Trigger: se aplican en respuesta a eventos específicos.
 *
 * Fórmulas de nivel:
 *  - La mayoría usa rangos de 5 niveles: breakpoint = (nivel-1) / 5
 *  - Tigre usa rangos de 2: breakpoint = (nivel-1) / 2
 */
public class AbilityManager {

    // Namespace para todos los AttributeModifier de este plugin
    private static final String NS = "betterpets";

    // Claves de modificador por atributo (deben ser únicas por atributo)
    private static final String KEY_FALL    = "fall_dmg_mult";
    private static final String KEY_OXY     = "oxygen_bonus";
    private static final String KEY_SCALE   = "scale";
    private static final String KEY_STEP    = "step_height";
    private static final String KEY_HEALTH  = "max_health";
    private static final String KEY_ATK_KB  = "atk_knockback";
    private static final String KEY_ATK_SPD = "atk_speed";
    private static final String KEY_ATK_DMG = "atk_damage";
    private static final String KEY_SPEED   = "move_speed";
    private static final String KEY_WATER   = "water_move";
    private static final String KEY_SUBMRG  = "submrg_mine";
    private static final String KEY_MINE    = "mine_eff";
    private static final String KEY_JUMP    = "jump_str";
    private static final String KEY_LUCK    = "luck";
    private static final String KEY_KB_RES  = "kb_resist";
    private static final String KEY_EXP_KB  = "exp_kb_res";
    private static final String KEY_SNEAK   = "sneak_spd";
    private static final String KEY_SWEEP   = "sweep_dmg";
    private static final String KEY_SAFE    = "safe_fall";

    // Valores default de atributos del jugador en 1.21.1
    // (para reset limpio al quitar mascota)
    private static final Map<String, Double> DEFAULTS = new HashMap<>();
    static {
        DEFAULTS.put(KEY_FALL,   1.0);
        DEFAULTS.put(KEY_OXY,    0.0);
        DEFAULTS.put(KEY_SCALE,  1.0);
        DEFAULTS.put(KEY_STEP,   0.6);
        DEFAULTS.put(KEY_HEALTH, 20.0);
        DEFAULTS.put(KEY_ATK_KB, 0.0);
        DEFAULTS.put(KEY_ATK_SPD,4.0);
        DEFAULTS.put(KEY_ATK_DMG,1.0);
        DEFAULTS.put(KEY_SPEED,  0.1);
        DEFAULTS.put(KEY_WATER,  0.0);
        DEFAULTS.put(KEY_SUBMRG, 0.2);
        DEFAULTS.put(KEY_MINE,   0.0);
        DEFAULTS.put(KEY_JUMP,   0.42);
        DEFAULTS.put(KEY_LUCK,   0.0);
        DEFAULTS.put(KEY_KB_RES, 0.0);
        DEFAULTS.put(KEY_EXP_KB, 0.0);
        DEFAULTS.put(KEY_SNEAK,  0.3);
        DEFAULTS.put(KEY_SWEEP,  1.0);
        DEFAULTS.put(KEY_SAFE,   3.0);
    }

    private final BetterPetsPlugin plugin;
    // jugadores con la mascota guardian: inmunes al Warden
    private final Set<UUID> wardenImmune = new HashSet<>();

    public AbilityManager(BetterPetsPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================
    //  APLICAR HABILIDADES PASIVAS
    // =========================================================

    /**
     * Aplica la habilidad pasiva de la mascota al jugador.
     * Llamado cada X ticks desde PetTickTask.
     */
    public void applyPassiveAbility(Player player, PetData pet) {
        int level = pet.getLevel();
        PetType type = pet.getType();

        // Remover todos los modificadores de este plugin primero
        removeAllModifiers(player);

        switch (type) {
            case HORMIGA       -> applyScale(player, level, true);    // shrink
            case AJOLOTE       -> applyOxygenBonus(player, level);
            case DRAGON_AZUL   -> applyDragonBlue(player, level);
            case GATO          -> applyFallDamage(player, level);
            case GALLINA       -> { /* tick manejado por tarea periódica */ }
            case PERRO         -> { /* trigger: manejado en EntityListener */ }
            case DELFIN        -> applyDolphin(player, level);
            case ELDER_GUARDIAN-> applySubmergedMining(player, level);
            case GHAST         -> applyExplosionResist(player, level);
            case HAMSTER       -> applyStepHeight(player, level);
            case HEROBRINE     -> applyHerobrine(player, level);
            case BUHO          -> applyOwl(player, level);
            case PANDA         -> applyAttackKnockback(player, level);
            case PINGUINO      -> applyPenguin(player, level);
            case DRAGON_ROJO   -> applyDragonRed(player, level);
            case LORO_ROJO     -> { /* tick manejado por tarea periódica */ }
            case FENIX         -> applyPhoenix(player, level);
            case PEZ_GLOBO     -> applyPufferfish(player, level);
            case CONEJO        -> applyRabbit(player, level);
            case SEGADOR       -> applyReaper(player, level);
            case CARACOL       -> applySneakSpeed(player, level);
            case ESPINOSAURIO  -> applyScale(player, level, false);   // grow
            case TIGRE         -> applyTiger(player, level);
            case TORTUGA       -> applyKnockbackResist(player, level);
            case GUARDIAN      -> applyWarden(player);
            case GUSANO        -> applyMiningEfficiency(player, level);
        }
    }

    /**
     * Remueve todos los efectos pasivos de la mascota del jugador.
     * Llamado al despawnear o cambiar mascota.
     */
    public void removeAllPassiveEffects(Player player) {
        removeAllModifiers(player);

        // Remover efectos de pociones otorgados por mascotas
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.WATER_BREATHING);

        // Limpiar inmunidad al Guardián
        wardenImmune.remove(player.getUniqueId());

        // Restaurar clima si tenía Herobrine (solo si nadie más lo usa)
        // No forzamos el clima al quitarlo para no interferir con otros plugins
    }

    // =========================================================
    //  TRIGGER ABILITIES (llamadas desde listeners)
    // =========================================================

    /**
     * Perro: al recibir daño de un no-muerto, aplica Marchitamiento.
     * Retorna true si se aplicó el efecto.
     */
    public boolean triggerDogAbility(Player player, Entity attacker, int level) {
        if (!(attacker instanceof LivingEntity livingAttacker)) return false;
        if (!isUndead(attacker)) return false;

        double chance = getLinearValue(level, 5, 0.05, 0.05);
        if (Math.random() < chance) {
            livingAttacker.addPotionEffect(
                new PotionEffect(PotionEffectType.WITHER, 40, 1, false, true)
            );
            return true;
        }
        return false;
    }

    /**
     * Segador: aura que aplica Marchitamiento a entidades cercanas.
     * Llamado cada 3 segundos por PetTickTask.
     */
    public void triggerReaperAura(Player player, int level) {
        int radio = plugin.getConfig().getInt("mascotas.segador.radio-aura", 9);
        double chance = getLinearValue(level, 5, 0.1, 0.1);

        player.getWorld().getNearbyEntities(player.getLocation(), radio, radio, radio)
            .stream()
            .filter(e -> e instanceof LivingEntity && e != player && isHostile(e))
            .forEach(e -> {
                if (Math.random() < chance) {
                    ((LivingEntity) e).addPotionEffect(
                        new PotionEffect(PotionEffectType.WITHER, 40, 0, false, true)
                    );
                }
            });
    }

    /**
     * Segador - Cosecha Oscura: cuando el jugador golpea un enemigo herido.
     * Llamado en EntityDamageByEntityEvent.
     */
    public void triggerReaperHarvest(Player player, Entity target, int level) {
        // Efecto visual oscuro al cosechar
        player.getWorld().spawnParticle(Particle.WITCH, target.getLocation(), 8, 0.3, 0.5, 0.3, 0.0);
    }

    /**
     * Fénix: enciende a los no-muertos cercanos al atacarlos.
     */
    public void triggerPhoenixFire(Player player, Entity target, int level) {
        if (!isUndead(target)) return;
        int fireTicks = (int) getLinearValue(level, 5, 10, 10);
        target.setFireTicks(fireTicks);
    }

    /**
     * Pufferfish: alerta/inflinge daño a los enemigos al recibir daño.
     */
    public void triggerPufferfishAlert(Player player, int level) {
        int radio = 5;
        player.getWorld().getNearbyEntities(player.getLocation(), radio, radio, radio)
            .stream()
            .filter(e -> e instanceof LivingEntity && e != player && isHostile(e))
            .forEach(e -> {
                if (e instanceof LivingEntity le) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true));
                }
            });
    }

    /**
     * Dragón Rojo: trigger de fuego en el Nether (partículas de llama).
     */
    public void triggerRedDragon(Player player, Entity target, int level) {
        if (player.getWorld().getEnvironment() != World.Environment.NETHER) return;
        player.getWorld().spawnParticle(Particle.FLAME, target.getLocation(), 12, 0.3, 0.5, 0.3, 0.05);
    }

    /**
     * Dragón Azul: trigger en el End.
     */
    public void triggerBlueDragon(Player player, Entity target, int level) {
        if (player.getWorld().getEnvironment() != World.Environment.THE_END) return;
        player.getWorld().spawnParticle(Particle.DRAGON_BREATH, target.getLocation(), 12, 0.3, 0.5, 0.3, 0.05);
    }

    /**
     * Loro Rojo: genera un ítem aleatorio.
     */
    public void triggerRedParrot(Player player) {
        List<String> items = plugin.getPetsConfig().getStringList("mascotas.loro_rojo.objetos-posibles");
        if (items.isEmpty()) return;
        String matName = items.get((int)(Math.random() * items.size()));
        Material mat;
        try { mat = Material.valueOf(matName); }
        catch (Exception e) { mat = Material.GOLD_NUGGET; }
        player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(mat));
    }

    /**
     * Gallina: genera un huevo.
     */
    public void triggerChickenEgg(Player player) {
        player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(Material.EGG));
    }

    /**
     * Warden Guardián: comprueba si el jugador es inmune.
     */
    public boolean isWardenImmune(Player player) {
        return wardenImmune.contains(player.getUniqueId());
    }

    // =========================================================
    //  HABILIDADES PASIVAS INDIVIDUALES
    // =========================================================

    /** Hormiga (shrink=true) / Espinosaurio (shrink=false): escala del jugador */
    private void applyScale(Player player, int level, boolean shrink) {
        // Rango de 5 niveles, empezando desde ±2.5% por rango
        int bp = (level - 1) / 5;
        double delta = 0.025 * (bp + 1);
        double value = shrink ? (1.0 - delta) : (1.0 + delta);
        value = Math.max(0.1, Math.min(5.0, value)); // límites de seguridad
        setAttributeBase(player, Attribute.GENERIC_SCALE, value, KEY_SCALE);
    }

    /** Ajolote: bonus de oxígeno */
    private void applyOxygenBonus(Player player, int level) {
        int bp = (level - 1) / 5;
        double value = 0.5 * (bp + 1); // 0.5, 1.0, 1.5 ... hasta 10.0 en nivel 100
        setAttributeBase(player, Attribute.GENERIC_OXYGEN_BONUS, value, KEY_OXY);
    }

    /** Dragón Azul: daño de ataque en el End */
    private void applyDragonBlue(Player player, int level) {
        if (player.getWorld().getEnvironment() != World.Environment.THE_END) {
            resetAttribute(player, Attribute.GENERIC_ATTACK_DAMAGE, KEY_ATK_DMG);
            return;
        }
        int bp = (level - 1) / 5;
        double value = 2.3 + (0.3 * bp);
        setAttributeBase(player, Attribute.GENERIC_ATTACK_DAMAGE, value, KEY_ATK_DMG);
    }

    /** Gato: reducir multiplicador de daño de caída */
    private void applyFallDamage(Player player, int level) {
        int bp = (level - 1) / 5;
        double value = 1.0 - (0.05 * (bp + 1));
        value = Math.max(0.0, value);
        setAttributeBase(player, Attribute.GENERIC_FALL_DAMAGE_MULTIPLIER, value, KEY_FALL);
    }

    /** Delfín: efecto Gracia de Delfín + velocidad en agua */
    private void applyDolphin(Player player, int level) {
        player.addPotionEffect(
            new PotionEffect(PotionEffectType.DOLPHINS_GRACE, Integer.MAX_VALUE, 0, false, false, false)
        );
        int bp = (level - 1) / 5;
        double value = 0.05 * (bp + 1); // 0.05 hasta 1.0
        setAttributeBase(player, Attribute.GENERIC_WATER_MOVEMENT_EFFICIENCY, value, KEY_WATER);
    }

    /** Elder Guardián: velocidad de minado bajo el agua */
    private void applySubmergedMining(Player player, int level) {
        int bp = (level - 1) / 5;
        double value = 0.2 + (0.04 * (bp + 1)); // base 0.2 + incremento
        setAttributeBase(player, Attribute.PLAYER_SUBMERGED_MINING_SPEED, value, KEY_SUBMRG);
    }

    /** Ghast: resistencia a explosiones */
    private void applyExplosionResist(Player player, int level) {
        int bp = (level - 1) / 5;
        double value = 0.05 * (bp + 1);
        value = Math.min(1.0, value);
        setAttributeBase(player, Attribute.GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE, value, KEY_EXP_KB);
    }

    /** Hámster: altura de paso */
    private void applyStepHeight(Player player, int level) {
        int bp = (level - 1) / 5;
        double value = 0.6 + (0.045 * (bp + 1)); // 0.645, 0.69, ...
        setAttributeBase(player, Attribute.GENERIC_STEP_HEIGHT, value, KEY_STEP);
    }

    /** Herobrine: salud máxima + tormenta */
    private void applyHerobrine(Player player, int level) {
        int bp = (level - 1) / 5;
        double value = 20.0 + (bp + 1); // 21, 22, 23 ... hasta 40
        setAttributeBase(player, Attribute.GENERIC_MAX_HEALTH, value, KEY_HEALTH);
        // Tormenta cada aplicación (solo si el mundo lo permite)
        if (!player.getWorld().isThundering()) {
            player.getWorld().setStorm(true);
            player.getWorld().setThundering(true);
        }
    }

    /** Búho: visión nocturna + suerte */
    private void applyOwl(Player player, int level) {
        player.addPotionEffect(
            new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false)
        );
        int bp = (level - 1) / 5;
        double luck = 25.0 * (bp + 1); // 25, 50, 75 ... hasta 500
        setAttributeBase(player, Attribute.GENERIC_LUCK, luck, KEY_LUCK);
    }

    /** Panda: retroceso de ataque */
    private void applyAttackKnockback(Player player, int level) {
        int bp = (level - 1) / 5;
        double value = 0.05 * (bp + 1);
        setAttributeBase(player, Attribute.GENERIC_ATTACK_KNOCKBACK, value, KEY_ATK_KB);
    }

    /** Pingüino: buffs en biomas fríos */
    private void applyPenguin(Player player, int level) {
        List<String> coldBiomes = plugin.getPetsConfig()
            .getStringList("mascotas.pinguino.biomas-frios");
        String biome = player.getLocation().getBlock().getBiome().name();
        boolean isCold = coldBiomes.contains(biome);

        if (isCold) {
            // En bioma frío: velocidad de ataque y resistencia
            setAttributeBase(player, Attribute.GENERIC_MOVEMENT_SPEED, 0.115, KEY_SPEED);
            setAttributeBase(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, 0.1, KEY_KB_RES);
        } else {
            resetAttribute(player, Attribute.GENERIC_MOVEMENT_SPEED, KEY_SPEED);
            resetAttribute(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, KEY_KB_RES);
        }
    }

    /** Dragón Rojo: daño de ataque en el Nether */
    private void applyDragonRed(Player player, int level) {
        if (player.getWorld().getEnvironment() != World.Environment.NETHER) {
            resetAttribute(player, Attribute.GENERIC_ATTACK_DAMAGE, KEY_ATK_DMG);
            return;
        }
        int bp = (level - 1) / 5;
        double value = 2.3 + (0.3 * bp);
        setAttributeBase(player, Attribute.GENERIC_ATTACK_DAMAGE, value, KEY_ATK_DMG);
    }

    /** Fénix: resistencia al fuego + activación por evento */
    private void applyPhoenix(Player player, int level) {
        player.addPotionEffect(
            new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, false)
        );
    }

    /** Pez Globo: respiración acuática */
    private void applyPufferfish(Player player, int level) {
        player.addPotionEffect(
            new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 0, false, false, false)
        );
    }

    /** Conejo: salto + distancia de caída segura */
    private void applyRabbit(Player player, int level) {
        setAttributeBase(player, Attribute.GENERIC_SAFE_FALL_DISTANCE, 10.0, KEY_SAFE);
        int bp = (level - 1) / 5;
        double jump = 0.42 + (0.0316 * (bp + 1));
        setAttributeBase(player, Attribute.GENERIC_JUMP_STRENGTH, jump, KEY_JUMP);
    }

    /** Segador: velocidad de ataque */
    private void applyReaper(Player player, int level) {
        int bp = (level - 1) / 5;
        double value = 4.0 + (0.2 * (bp + 1));
        setAttributeBase(player, Attribute.GENERIC_ATTACK_SPEED, value, KEY_ATK_SPD);
    }

    /** Caracol: velocidad agachado */
    private void applySneakSpeed(Player player, int level) {
        int bp = (level - 1) / 5;
        double value = 0.3 + (0.035 * (bp + 1));
        setAttributeBase(player, Attribute.GENERIC_SNEAKING_SPEED, value, KEY_SNEAK);
    }

    /** Tigre: velocidad de movimiento + daño de barrido */
    private void applyTiger(Player player, int level) {
        // Rangos de 2 niveles (como en el datapack)
        int bp = (level - 1) / 2;
        double speed = 0.1 + (0.002 * (bp + 1));
        setAttributeBase(player, Attribute.GENERIC_MOVEMENT_SPEED, speed, KEY_SPEED);
        // Daño de barrido máximo
        setAttributeBase(player, Attribute.PLAYER_SWEEPING_DAMAGE_RATIO, 1.0, KEY_SWEEP);
    }

    /** Tortuga: resistencia al retroceso */
    private void applyKnockbackResist(Player player, int level) {
        int bp = (level - 1) / 5;
        double value = 5.0 * (bp + 1); // 5, 10, 15 ... (no se cap en 1.21)
        setAttributeBase(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, value, KEY_KB_RES);
    }

    /** Guardián: hace al jugador inmune al Warden */
    private void applyWarden(Player player) {
        wardenImmune.add(player.getUniqueId());
    }

    /** Gusano: eficiencia de minado */
    private void applyMiningEfficiency(Player player, int level) {
        int bp = (level - 1) / 5;
        double value = 0.5 * (bp + 1);
        setAttributeBase(player, Attribute.PLAYER_MINING_EFFICIENCY, value, KEY_MINE);
    }

    // =========================================================
    //  UTILIDADES DE ATRIBUTOS
    // =========================================================

    /**
     * Establece el valor base de un atributo del jugador usando un AttributeModifier
     * con NamespacedKey del plugin. Remueve el modificador previo antes de agregar.
     */
    private void setAttributeBase(Player player, Attribute attribute, double targetValue, String modKey) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;

        // Remover modificador anterior de este plugin para este atributo
        NamespacedKey nsKey = new NamespacedKey(plugin, modKey);
        inst.getModifiers().stream()
            .filter(m -> m.getKey().equals(nsKey))
            .forEach(inst::removeModifier);

        // El modificador ADD_NUMBER suma a la base. Calculamos el delta necesario.
        double delta = targetValue - inst.getBaseValue();
        if (Math.abs(delta) < 0.00001) return; // ya tiene el valor correcto

        AttributeModifier modifier = new AttributeModifier(
            nsKey, delta, AttributeModifier.Operation.ADD_NUMBER
        );
        inst.addModifier(modifier);
    }

    /** Restablece el atributo a su valor por defecto removiendo el modificador. */
    private void resetAttribute(Player player, Attribute attribute, String modKey) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;
        NamespacedKey nsKey = new NamespacedKey(plugin, modKey);
        inst.getModifiers().stream()
            .filter(m -> m.getKey().equals(nsKey))
            .forEach(inst::removeModifier);
    }

    /** Remueve TODOS los modificadores de BetterPets del jugador. */
    public void removeAllModifiers(Player player) {
        Attribute[] attrs = {
            Attribute.GENERIC_FALL_DAMAGE_MULTIPLIER,
            Attribute.GENERIC_OXYGEN_BONUS,
            Attribute.GENERIC_SCALE,
            Attribute.GENERIC_STEP_HEIGHT,
            Attribute.GENERIC_MAX_HEALTH,
            Attribute.GENERIC_ATTACK_KNOCKBACK,
            Attribute.GENERIC_ATTACK_SPEED,
            Attribute.GENERIC_ATTACK_DAMAGE,
            Attribute.GENERIC_MOVEMENT_SPEED,
            Attribute.GENERIC_WATER_MOVEMENT_EFFICIENCY,
            Attribute.PLAYER_SUBMERGED_MINING_SPEED,
            Attribute.PLAYER_MINING_EFFICIENCY,
            Attribute.GENERIC_JUMP_STRENGTH,
            Attribute.GENERIC_LUCK,
            Attribute.GENERIC_KNOCKBACK_RESISTANCE,
            Attribute.GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE,
            Attribute.GENERIC_SNEAKING_SPEED,
            Attribute.PLAYER_SWEEPING_DAMAGE_RATIO,
            Attribute.GENERIC_SAFE_FALL_DISTANCE
        };
        for (Attribute attr : attrs) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) continue;
            inst.getModifiers().stream()
                .filter(m -> m.getKey().getNamespace().equals(NS))
                .forEach(inst::removeModifier);
        }
        // Remover inmunidad al Guardián
        wardenImmune.remove(player.getUniqueId());
    }

    // =========================================================
    //  UTILIDADES GENERALES
    // =========================================================

    /** Calcula un valor lineal: start + step * breakpoint donde bp = (nivel-1) / range */
    private double getLinearValue(int level, int range, double start, double step) {
        int bp = (level - 1) / range;
        return start + (step * bp);
    }

    /** Comprueba si una entidad es de tipo no-muerto */
    public static boolean isUndead(Entity entity) {
        return entity instanceof Zombie || entity instanceof Skeleton
            || entity instanceof Wither || entity instanceof ZombieVillager
            || entity instanceof Stray || entity instanceof Husk
            || entity instanceof WitherSkeleton || entity instanceof Drowned
            || entity instanceof Phantom || entity instanceof ZombifiedPiglin
            || entity instanceof Zoglin;
    }

    /** Comprueba si una entidad es hostil */
    private boolean isHostile(Entity entity) {
        return entity instanceof Monster || entity instanceof Slime
            || entity instanceof Ghast || entity instanceof MagmaCube;
    }
}
