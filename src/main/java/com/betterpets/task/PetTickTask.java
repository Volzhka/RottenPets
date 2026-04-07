package com.betterpets.task;

import com.betterpets.BetterPetsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tarea repetitiva que gestiona el seguimiento de mascotas
 * y la aplicación de habilidades pasivas.
 *
 * Usa un contador interno para ejecutar acciones con distintas frecuencias:
 *  - Seguimiento: cada ticks-seguimiento (10 ticks = 0.5s por defecto)
 *  - Habilidades: cada ticks-habilidades (20 ticks = 1s por defecto)
 *  - Guardado:    cada auto-guardado segundos
 */
public class PetTickTask extends BukkitRunnable {

    private final BetterPetsPlugin plugin;
    private long tick = 0;

    private final long followInterval;
    private final long abilityInterval;
    private final long saveInterval; // en ticks

    public PetTickTask(BetterPetsPlugin plugin) {
        this.plugin = plugin;
        this.followInterval  = plugin.getConfig().getLong("mascota.ticks-seguimiento", 10);
        this.abilityInterval = plugin.getConfig().getLong("mascota.ticks-habilidades", 20);
        long autoSaveSecs = plugin.getConfig().getLong("auto-guardado", 300);
        this.saveInterval = autoSaveSecs > 0 ? autoSaveSecs * 20 : Long.MAX_VALUE;
    }

    @Override
    public void run() {
        tick++;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // Seguimiento de mascota
            if (tick % followInterval == 0) {
                plugin.getPetManager().tickFollowPet(player);
            }
            // Habilidades pasivas
            if (tick % abilityInterval == 0) {
                plugin.getPetManager().tickAbilities(player);
            }
        }

        // Auto-guardado
        if (saveInterval != Long.MAX_VALUE && tick % saveInterval == 0) {
            plugin.getPetManager().saveAll();
        }
    }
}
