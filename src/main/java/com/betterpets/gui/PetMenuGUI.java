package com.betterpets.gui;

import com.betterpets.BetterPetsPlugin;
import com.betterpets.data.PetData;
import com.betterpets.data.PetType;
import com.betterpets.data.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Interfaz gráfica (GUI) del menú de mascotas.
 * InventoryHolder personalizado para identificar fácilmente el menú.
 *
 * Layout (5 filas = 45 slots):
 * ┌─────────────────────────────────────────────────────┐
 * │  0  1  2  3  4  5  6  7  8  │  ← Fila 1 (pets 0-8) │
 * │  9 10 11 12 13 14 15 16 17  │  ← Fila 2 (pets 9-17)│
 * │ 18 19 20 21 22 23 24 25 26  │  ← Fila 3 (relleno)  │
 * │ 27 28 29 30 31 32 33 34 35  │  ← Fila 4 (vacío)    │
 * │ 36 37 38 39 40 41 42 43 44  │  ← Fila 5 (controles)│
 * └─────────────────────────────────────────────────────┘
 *
 * Slots de control (fila 5):
 * 36 = Nivel display | 37 = Visibilidad | 38-39 = Glass | 40 = Cerrar
 * 41 = Despawnear | 42 = Convertir | 43-43 = Glass | 44 = Pág siguiente/anterior
 *
 * NOTA: Las mascotas se muestran en slots 0-17 (18 por página).
 */
public class PetMenuGUI implements InventoryHolder {

    // IDs de acción (guardados en el PDC de los items del menú)
    public static final String ACTION_CLOSE       = "close";
    public static final String ACTION_DESPAWN      = "despawn";
    public static final String ACTION_CONVERT      = "convert";
    public static final String ACTION_VISIBILITY   = "visibility";
    public static final String ACTION_NEXT_PAGE    = "next_page";
    public static final String ACTION_PREV_PAGE    = "prev_page";
    public static final String ACTION_ACTIVATE_PET = "activate_pet:";

    public static final int PETS_PER_PAGE = 18;
    public static final int[] PET_SLOTS = new int[PETS_PER_PAGE];

    static {
        for (int i = 0; i < PETS_PER_PAGE; i++) PET_SLOTS[i] = i;
    }

    // Slots fijos de controles en fila 5 (índices 36-44)
    public static final int SLOT_LEVEL   = 36;
    public static final int SLOT_VISIB   = 37;
    public static final int SLOT_FILL_A  = 38;
    public static final int SLOT_FILL_B  = 39;
    public static final int SLOT_CLOSE   = 40;
    public static final int SLOT_DESPAWN = 41;
    public static final int SLOT_CONVERT = 42;
    public static final int SLOT_FILL_C  = 43;
    public static final int SLOT_PAGE    = 44;

    private final BetterPetsPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final int page;

    public PetMenuGUI(BetterPetsPlugin plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page   = page;

        String titulo = ChatColor.translateAlternateColorCodes('&',
            plugin.getMsgManager().getRaw("menu.titulo")
                .replace("{0}", String.valueOf(page + 1)));

        this.inventory = plugin.getServer().createInventory(this, 45, titulo);
        build();
    }

    private void build() {
        PlayerData data = plugin.getPetManager().getPlayerData(player.getUniqueId());

        // --- Limpiar ---
        inventory.clear();

        // --- Slots de mascotas (0-17) ---
        int start = page * PETS_PER_PAGE;
        for (int i = 0; i < PETS_PER_PAGE; i++) {
            int petIndex = start + i;
            if (petIndex < data.getPetCount()) {
                PetData pet = data.getPet(petIndex);
                inventory.setItem(i, buildPetSlot(pet, petIndex, data.getActivePetIndex() == petIndex));
            } else {
                inventory.setItem(i, buildFiller(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }

        // --- Filas 3 y 4 (slots 18-35): relleno decorativo ---
        for (int i = 18; i < 36; i++) {
            inventory.setItem(i, buildFiller(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        // --- Panel de nivel (slot 36) ---
        inventory.setItem(SLOT_LEVEL, buildLevelDisplay(data));

        // --- Visibilidad (slot 37) ---
        inventory.setItem(SLOT_VISIB, buildVisibilityItem(data));

        // --- Rellenos ---
        inventory.setItem(SLOT_FILL_A, buildFiller(Material.GRAY_STAINED_GLASS_PANE, " "));
        inventory.setItem(SLOT_FILL_B, buildFiller(Material.GRAY_STAINED_GLASS_PANE, " "));

        // --- Cerrar (slot 40) ---
        inventory.setItem(SLOT_CLOSE, buildActionItem(Material.BARRIER,
            plugin.getMsgManager().getRaw("menu.cerrar"),
            Collections.singletonList(plugin.getMsgManager().getRaw("menu.cerrar-lore")),
            ACTION_CLOSE));

        // --- Despawnear (slot 41) ---
        inventory.setItem(SLOT_DESPAWN, buildActionItem(Material.PURPLE_DYE,
            plugin.getMsgManager().getRaw("menu.despawnear"),
            Arrays.asList(plugin.getMsgManager().getRaw("menu.despawnear-lore").split("\n")),
            ACTION_DESPAWN));

        // --- Convertir (slot 42) ---
        inventory.setItem(SLOT_CONVERT, buildActionItem(Material.GRAY_DYE,
            plugin.getMsgManager().getRaw("menu.convertir"),
            Arrays.asList(plugin.getMsgManager().getRaw("menu.convertir-lore").split("\n")),
            ACTION_CONVERT));

        // --- Relleno ---
        inventory.setItem(SLOT_FILL_C, buildFiller(Material.GRAY_STAINED_GLASS_PANE, " "));

        // --- Página (slot 44) ---
        int totalPages = Math.max(1, (int) Math.ceil(data.getPetCount() / (double) PETS_PER_PAGE));
        inventory.setItem(SLOT_PAGE, buildPageItem(page, totalPages));
    }

    // =========================================================
    //  BUILDERS DE ITEMS
    // =========================================================

    private ItemStack buildPetSlot(PetData pet, int petIndex, boolean isActive) {
        String typeName = plugin.getPetManager().getPetDisplayName(pet.getType());
        String matName  = plugin.getPetsConfig().getString(
            "mascotas." + pet.getType().getKey() + ".material-menu", "ENCHANTED_BOOK");
        Material mat;
        try { mat = Material.valueOf(matName); }
        catch (Exception e) { mat = Material.ENCHANTED_BOOK; }

        long base = plugin.getConfig().getLong("exp.base", 50);
        long mult = plugin.getConfig().getLong("exp.multiplicador", 50);
        long expReq = pet.getExpRequired(base, mult);

        List<String> lore = new ArrayList<>();
        if (isActive) {
            lore.add(ChatColor.translateAlternateColorCodes('&',
                plugin.getMsgManager().getRaw("menu.mascota-activa")));
            lore.add("");
        }
        lore.add(ChatColor.translateAlternateColorCodes('&',
            plugin.getMsgManager().getRaw("menu.mascota-nivel").replace("{0}", String.valueOf(pet.getLevel()))));
        lore.add(ChatColor.translateAlternateColorCodes('&',
            plugin.getMsgManager().getRaw("menu.mascota-exp")
                .replace("{0}", String.valueOf(pet.getExp()))
                .replace("{1}", String.valueOf(expReq))));
        lore.add("");

        String habilidad = plugin.getPetsConfig().getString(
            "mascotas." + pet.getType().getKey() + ".habilidad", "");
        lore.add(ChatColor.translateAlternateColorCodes('&',
            plugin.getMsgManager().getRaw("menu.mascota-habilidad").replace("{0}", habilidad)));

        List<String> hDesc = plugin.getPetsConfig().getStringList(
            "mascotas." + pet.getType().getKey() + ".descripcion-habilidad");
        for (String line : hDesc) {
            lore.add(ChatColor.translateAlternateColorCodes('&', "&7  " + line));
        }
        lore.add("");
        lore.add(ChatColor.translateAlternateColorCodes('&', "&eHaz clic para activar"));

        String displayName = ChatColor.translateAlternateColorCodes('&',
            (isActive ? "&a★ " : "&f") + "[Niv " + pet.getLevel() + "] " + typeName);

        ItemStack item = buildItem(mat, displayName, lore);

        // Añadir acción en PDC
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            org.bukkit.NamespacedKey actionKey = new org.bukkit.NamespacedKey(plugin, "menu_action");
            meta.getPersistentDataContainer().set(actionKey,
                org.bukkit.persistence.PersistentDataType.STRING, ACTION_ACTIVATE_PET + petIndex);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildLevelDisplay(PlayerData data) {
        PetData pet = data.getActivePet();
        List<String> lore = new ArrayList<>();

        if (pet == null) {
            lore.add(ChatColor.translateAlternateColorCodes('&',
                plugin.getMsgManager().getRaw("menu.nivel-display-lore-sin")));
        } else {
            long base = plugin.getConfig().getLong("exp.base", 50);
            long mult = plugin.getConfig().getLong("exp.multiplicador", 50);
            String nombre = plugin.getPetManager().getPetDisplayName(pet.getType());
            String loreStr = plugin.getMsgManager().getRaw("menu.nivel-display-lore")
                .replace("{0}", nombre)
                .replace("{1}", String.valueOf(pet.getLevel()))
                .replace("{2}", String.valueOf(pet.getExp()))
                .replace("{3}", String.valueOf(pet.getExpRequired(base, mult)));
            for (String line : loreStr.split("\n")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
        }
        return buildActionItem(Material.BOOKSHELF,
            ChatColor.translateAlternateColorCodes('&', plugin.getMsgManager().getRaw("menu.nivel-display")),
            lore, "");
    }

    private ItemStack buildVisibilityItem(PlayerData data) {
        PetData pet = data.getActivePet();
        boolean visible = pet == null || pet.isVisible();

        String loreKey = visible ? "menu.visibilidad-lore-visible" : "menu.visibilidad-lore-oculta";
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getMsgManager().getRaw(loreKey).split("\n")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        Material mat = visible ? Material.ENDER_EYE : Material.ENDER_PEARL;
        return buildActionItem(mat,
            ChatColor.translateAlternateColorCodes('&', plugin.getMsgManager().getRaw("menu.visibilidad")),
            lore, ACTION_VISIBILITY);
    }

    private ItemStack buildPageItem(int currentPage, int totalPages) {
        boolean hasNext = currentPage < totalPages - 1;
        boolean hasPrev = currentPage > 0;

        if (hasNext && !hasPrev) {
            // Solo siguiente
            List<String> lore = Collections.singletonList(
                ChatColor.translateAlternateColorCodes('&',
                    plugin.getMsgManager().getRaw("menu.pagina-siguiente-lore")
                        .replace("{0}", String.valueOf(currentPage + 2))));
            return buildActionItem(Material.ARROW,
                ChatColor.translateAlternateColorCodes('&', plugin.getMsgManager().getRaw("menu.pagina-siguiente")),
                lore, ACTION_NEXT_PAGE);
        } else if (hasPrev && !hasNext) {
            // Solo anterior
            List<String> lore = Collections.singletonList(
                ChatColor.translateAlternateColorCodes('&',
                    plugin.getMsgManager().getRaw("menu.pagina-anterior-lore")
                        .replace("{0}", String.valueOf(currentPage))));
            return buildActionItem(Material.ARROW,
                ChatColor.translateAlternateColorCodes('&', plugin.getMsgManager().getRaw("menu.pagina-anterior")),
                lore, ACTION_PREV_PAGE);
        } else if (hasNext) {
            // Ambas (mostrar siguiente)
            List<String> lore = Collections.singletonList(
                ChatColor.translateAlternateColorCodes('&',
                    plugin.getMsgManager().getRaw("menu.pagina-siguiente-lore")
                        .replace("{0}", String.valueOf(currentPage + 2))));
            return buildActionItem(Material.ARROW,
                ChatColor.translateAlternateColorCodes('&', plugin.getMsgManager().getRaw("menu.pagina-siguiente")),
                lore, ACTION_NEXT_PAGE);
        } else {
            return buildFiller(Material.GRAY_STAINED_GLASS_PANE, " ");
        }
    }

    // =========================================================
    //  HELPERS DE ITEMS
    // =========================================================

    private ItemStack buildActionItem(Material mat, String name, List<String> lore, String action) {
        ItemStack item = buildItem(mat, name, lore);
        if (!action.isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                org.bukkit.NamespacedKey actionKey = new org.bukkit.NamespacedKey(plugin, "menu_action");
                meta.getPersistentDataContainer().set(actionKey,
                    org.bukkit.persistence.PersistentDataType.STRING, action);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            // Ocultar atributos por defecto
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildFiller(Material mat, String name) {
        return buildItem(mat, name, Collections.emptyList());
    }

    // =========================================================
    //  ACCESO
    // =========================================================

    @Override
    @NotNull
    public Inventory getInventory() { return inventory; }

    public void open() { player.openInventory(inventory); }

    public int getPage() { return page; }
}
