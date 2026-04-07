package com.betterpets.manager;

import com.betterpets.BetterPetsPlugin;
import com.betterpets.data.PetData;
import com.betterpets.data.PetType;
import com.betterpets.data.PlayerData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gestiona la carga y guardado de datos de jugadores en archivos YAML.
 * Ruta: plugins/BetterPets/players/<UUID>.yml
 */
public class StorageManager {

    private final BetterPetsPlugin plugin;
    private final File playersDir;

    public StorageManager(BetterPetsPlugin plugin) {
        this.plugin = plugin;
        this.playersDir = new File(plugin.getDataFolder(), "players");
        if (!playersDir.exists()) playersDir.mkdirs();
    }

    /**
     * Carga los datos de un jugador desde su archivo YAML.
     * Si no existe el archivo, retorna un PlayerData vacío.
     */
    public PlayerData loadPlayerData(UUID uuid) {
        File file = getPlayerFile(uuid);
        PlayerData data = new PlayerData(uuid);

        if (!file.exists()) return data;

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Cargar índice de mascota activa
        data.setActivePetIndex(cfg.getInt("active-pet", -1));

        // Cargar mascotas
        if (cfg.contains("pets")) {
            for (String key : cfg.getConfigurationSection("pets").getKeys(false)) {
                String path = "pets." + key;
                String typeKey = cfg.getString(path + ".type");
                PetType type = PetType.fromKey(typeKey);
                if (type == null) {
                    plugin.getLogger().warning("Tipo de mascota desconocido '" + typeKey 
                        + "' en datos del jugador " + uuid + ". Saltando...");
                    continue;
                }
                int level = cfg.getInt(path + ".level", 1);
                long exp = cfg.getLong(path + ".exp", 0);
                boolean visible = cfg.getBoolean(path + ".visible", true);
                data.addPet(new PetData(type, level, exp, visible));
            }
        }

        // Corregir índice activo por si quedó desincronizado
        if (data.getActivePetIndex() >= data.getPetCount()) {
            data.clearActivePet();
        }

        data.setDirty(false);
        return data;
    }

    /**
     * Guarda los datos de un jugador en su archivo YAML.
     */
    public void savePlayerData(PlayerData data) {
        File file = getPlayerFile(data.getPlayerUUID());
        FileConfiguration cfg = new YamlConfiguration();

        cfg.set("active-pet", data.getActivePetIndex());

        for (int i = 0; i < data.getPetCount(); i++) {
            PetData pet = data.getPet(i);
            String path = "pets." + i;
            cfg.set(path + ".type", pet.getType().getKey());
            cfg.set(path + ".level", pet.getLevel());
            cfg.set(path + ".exp", pet.getExp());
            cfg.set(path + ".visible", pet.isVisible());
        }

        try {
            cfg.save(file);
            data.setDirty(false);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, 
                "No se pudo guardar datos del jugador " + data.getPlayerUUID(), e);
        }
    }

    /** Guarda sólo si los datos han cambiado (dirty). */
    public void saveIfDirty(PlayerData data) {
        if (data.isDirty()) savePlayerData(data);
    }

    /** Borra el archivo de datos de un jugador. */
    public void deletePlayerData(UUID uuid) {
        File file = getPlayerFile(uuid);
        if (file.exists()) file.delete();
    }

    private File getPlayerFile(UUID uuid) {
        return new File(playersDir, uuid.toString() + ".yml");
    }
}
