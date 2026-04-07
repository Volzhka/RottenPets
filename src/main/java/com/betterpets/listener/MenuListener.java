package com.betterpets.listener;

import com.betterpets.BetterPetsPlugin;
import com.betterpets.gui.PetMenuGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Listener para interacciones con el menú GUI de mascotas.
 * Lee la acción almacenada en el PDC de cada ítem del menú.
 */
public class MenuListener implements Listener {

    private final BetterPetsPlugin plugin;

    public MenuListener(BetterPetsPlugin plugin) {
        this.plugin = plugin;
    }

    // Cancelar cualquier drag o shift-click en el menú
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PetMenuGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof PetMenuGUI
                || event.getDestination().getHolder() instanceof PetMenuGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory topInv = event.getView().getTopInventory();

        // Solo si es nuestro menú
        if (!(topInv.getHolder() instanceof PetMenuGUI gui)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Si el click fue fuera del inventario superior, ignorar
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(topInv)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        org.bukkit.NamespacedKey actionKey = new org.bukkit.NamespacedKey(plugin, "menu_action");
        if (!meta.getPersistentDataContainer().has(actionKey)) return;

        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || action.isEmpty()) return;

        handleAction(player, gui, action);
    }

    private void handleAction(Player player, PetMenuGUI gui, String action) {
        switch (action) {
            case PetMenuGUI.ACTION_CLOSE -> {
                player.closeInventory();
            }
            case PetMenuGUI.ACTION_DESPAWN -> {
                player.closeInventory();
                plugin.getPetManager().despawnActivePet(player);
            }
            case PetMenuGUI.ACTION_CONVERT -> {
                player.closeInventory();
                plugin.getPetManager().convertActivePet(player);
            }
            case PetMenuGUI.ACTION_VISIBILITY -> {
                plugin.getPetManager().toggleVisibility(player);
                // Refrescar el menú en la misma página
                plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.openPetMenu(player, gui.getPage()));
            }
            case PetMenuGUI.ACTION_NEXT_PAGE -> {
                plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.openPetMenu(player, gui.getPage() + 1));
            }
            case PetMenuGUI.ACTION_PREV_PAGE -> {
                if (gui.getPage() > 0) {
                    plugin.getServer().getScheduler().runTask(plugin,
                        () -> plugin.openPetMenu(player, gui.getPage() - 1));
                }
            }
            default -> {
                if (action.startsWith(PetMenuGUI.ACTION_ACTIVATE_PET)) {
                    String idxStr = action.substring(PetMenuGUI.ACTION_ACTIVATE_PET.length());
                    try {
                        int petIndex = Integer.parseInt(idxStr);
                        plugin.getPetManager().activatePet(player, petIndex);
                        // Refrescar menú
                        plugin.getServer().getScheduler().runTask(plugin,
                            () -> plugin.openPetMenu(player, gui.getPage()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }
}
