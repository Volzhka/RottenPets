package com.betterpets;

import com.betterpets.command.PetsCommand;
import com.betterpets.gui.PetMenuGUI;
import com.betterpets.listener.EntityListener;
import com.betterpets.listener.MenuListener;
import com.betterpets.listener.PlayerListener;
import com.betterpets.manager.*;
import com.betterpets.task.PetTickTask;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Clase principal del plugin BetterPets.
 * Inicializa y coordina todos los subsistemas.
 */
public class BetterPetsPlugin extends JavaPlugin {

    // --- Managers ---
    private StorageManager storageManager;
    private AbilityManager abilityManager;
    private PetManager     petManager;
    private MessageManager msgManager;

    // --- Configuraciones adicionales ---
    private FileConfiguration petsConfig;
    private FileConfiguration messagesConfig;
    private File petsConfigFile;
    private File messagesConfigFile;

    // --- Tarea principal ---
    private PetTickTask tickTask;

    @Override
    public void onEnable() {
        // Crear archivos de config por defecto si no existen
        saveDefaultConfig();
        saveDefaultResource("pets.yml");
        saveDefaultResource("messages.yml");

        // Cargar configs
        reloadAllConfigs();

        // Inicializar managers (orden importa)
        this.storageManager = new StorageManager(this);
        this.abilityManager = new AbilityManager(this);
        this.petManager     = new PetManager(this);
        this.msgManager     = new MessageManager(this);

        // Registrar listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityListener(this), this);

        // Registrar comandos
        PetsCommand petsCmd = new PetsCommand(this);
        registerCommand("mascotas", petsCmd);
        registerCommand("mascota", petsCmd);

        // Registrar receta del menú
        if (getConfig().getBoolean("receta-menu.activada", true)) {
            registerMenuRecipe();
        }

        // Iniciar tarea repetitiva (cada 1 tick para manejar los intervalos internamente)
        tickTask = new PetTickTask(this);
        tickTask.runTaskTimer(this, 20L, 1L);

        // Cargar jugadores que ya están en línea (por recargas)
        for (Player player : getServer().getOnlinePlayers()) {
            petManager.loadPlayer(player);
        }

        getLogger().info("§a¡BetterPets habilitado correctamente! §726 mascotas disponibles.");
    }

    @Override
    public void onDisable() {
        // Cancelar tarea
        if (tickTask != null) tickTask.cancel();

        // Descargar todos los jugadores en línea (guarda datos y despawnea mascotas)
        for (Player player : getServer().getOnlinePlayers()) {
            petManager.unloadPlayer(player);
        }

        getLogger().info("§cBetterPets deshabilitado. Todos los datos guardados.");
    }

    // =========================================================
    //  CONFIGURACIONES
    // =========================================================

    private void reloadAllConfigs() {
        reloadConfig();
        reloadPetsConfig();
        reloadMessagesConfig();
    }

    private void reloadPetsConfig() {
        petsConfigFile = new File(getDataFolder(), "pets.yml");
        if (!petsConfigFile.exists()) saveDefaultResource("pets.yml");
        petsConfig = YamlConfiguration.loadConfiguration(petsConfigFile);
    }

    private void reloadMessagesConfig() {
        messagesConfigFile = new File(getDataFolder(), "messages.yml");
        if (!messagesConfigFile.exists()) saveDefaultResource("messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesConfigFile);
    }

    /**
     * Recarga toda la configuración del plugin.
     * Llamado desde /mascotas recargar.
     */
    public void reloadPluginConfig() {
        reloadAllConfigs();
        if (msgManager != null) msgManager.reload();
    }

    private void saveDefaultResource(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) saveResource(name, false);
    }

    public FileConfiguration getPetsConfig()     { return petsConfig; }
    public FileConfiguration getMessagesConfig() { return messagesConfig; }

    // =========================================================
    //  ACCESO A MANAGERS
    // =========================================================

    public StorageManager getStorageManager() { return storageManager; }
    public AbilityManager getAbilityManager() { return abilityManager; }
    public PetManager     getPetManager()     { return petManager; }
    public MessageManager getMsgManager()     { return msgManager; }

    // =========================================================
    //  MENÚ
    // =========================================================

    /**
     * Abre el menú de mascotas para el jugador en la página indicada.
     */
    public void openPetMenu(Player player, int page) {
        // Validar página
        int petCount = petManager.getPlayerData(player.getUniqueId()).getPetCount();
        int maxPage  = Math.max(0, (int) Math.ceil(petCount / (double) PetMenuGUI.PETS_PER_PAGE) - 1);
        page = Math.max(0, Math.min(page, maxPage));

        new PetMenuGUI(this, player, page).open();
    }

    // =========================================================
    //  RECETA DEL MENÚ
    // =========================================================

    private void registerMenuRecipe() {
        try {
            org.bukkit.NamespacedKey recipeKey = new org.bukkit.NamespacedKey(this, "pet_menu_key");
            // Quitar receta anterior si existe (para recargas)
            getServer().removeRecipe(recipeKey);

            org.bukkit.inventory.ShapedRecipe recipe = new org.bukkit.inventory.ShapedRecipe(
                recipeKey, petManager.createMenuKey());

            // Patrón:
            //  _ B _
            //  A _ A
            //  _ C _
            // donde: A = BONE, B = HEART_OF_THE_SEA, C = CHEST
            recipe.shape(" B ", "A A", " C ");

            String centro = getConfig().getString("receta-menu.ingredientes.centro", "HEART_OF_THE_SEA");
            String izq    = getConfig().getString("receta-menu.ingredientes.izquierda", "BONE");
            String abajo  = getConfig().getString("receta-menu.ingredientes.derecha", "CHEST");

            try {
                recipe.setIngredient('B', org.bukkit.Material.valueOf(centro));
                recipe.setIngredient('A', org.bukkit.Material.valueOf(izq));
                recipe.setIngredient('C', org.bukkit.Material.valueOf(abajo));
                getServer().addRecipe(recipe);
                getLogger().info("Receta del Cristal de Mascotas registrada.");
            } catch (IllegalArgumentException e) {
                getLogger().warning("Material inválido en receta-menu: " + e.getMessage());
            }
        } catch (Exception e) {
            getLogger().warning("No se pudo registrar la receta del menú: " + e.getMessage());
        }
    }

    // =========================================================
    //  UTILIDAD
    // =========================================================

    private void registerCommand(String name, CommandExecutor executor) {
        var cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter tc) {
                cmd.setTabCompleter(tc);
            }
        } else {
            getLogger().warning("Comando '" + name + "' no encontrado en plugin.yml");
        }
    }
}
