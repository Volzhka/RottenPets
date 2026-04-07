package com.betterpets.data;

import java.util.*;

/**
 * Datos de un jugador: colección de mascotas y mascota activa.
 */
public class PlayerData {

    private final UUID playerUUID;
    private final List<PetData> pets;
    private int activePetIndex; // -1 = ninguna
    private boolean dirty; // requiere guardado

    public PlayerData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.pets = new ArrayList<>();
        this.activePetIndex = -1;
        this.dirty = false;
    }

    // --- Colección de mascotas ---
    public List<PetData> getPets() { return pets; }

    public void addPet(PetData pet) {
        pets.add(pet);
        dirty = true;
    }

    public boolean removePet(int index) {
        if (index < 0 || index >= pets.size()) return false;
        pets.remove(index);
        if (activePetIndex == index) activePetIndex = -1;
        else if (activePetIndex > index) activePetIndex--;
        dirty = true;
        return true;
    }

    public PetData getPet(int index) {
        if (index < 0 || index >= pets.size()) return null;
        return pets.get(index);
    }

    public int getPetCount() { return pets.size(); }

    // --- Mascota activa ---
    public PetData getActivePet() {
        if (activePetIndex < 0 || activePetIndex >= pets.size()) return null;
        return pets.get(activePetIndex);
    }

    public int getActivePetIndex() { return activePetIndex; }

    public void setActivePetIndex(int index) {
        this.activePetIndex = index;
        dirty = true;
    }

    public void clearActivePet() {
        activePetIndex = -1;
        dirty = true;
    }

    // --- Utilidades ---
    public boolean hasPetOfType(PetType type) {
        return pets.stream().anyMatch(p -> p.getType() == type);
    }

    public UUID getPlayerUUID() { return playerUUID; }

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }
    public void markDirty() { this.dirty = true; }
}
