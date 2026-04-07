package com.betterpets.data;

/**
 * Datos de una instancia de mascota (nivel, EXP, visibilidad).
 */
public class PetData {

    private final PetType type;
    private int level;
    private long exp;
    private boolean visible;

    public PetData(PetType type) {
        this.type = type;
        this.level = 1;
        this.exp = 0;
        this.visible = true;
    }

    public PetData(PetType type, int level, long exp, boolean visible) {
        this.type = type;
        this.level = Math.max(1, Math.min(100, level));
        this.exp = Math.max(0, exp);
        this.visible = visible;
    }

    // --- Getters ---
    public PetType getType()     { return type; }
    public int getLevel()        { return level; }
    public long getExp()         { return exp; }
    public boolean isVisible()   { return visible; }

    // --- Setters ---
    public void setLevel(int level)    { this.level = Math.max(1, Math.min(100, level)); }
    public void setExp(long exp)       { this.exp = Math.max(0, exp); }
    public void setVisible(boolean v)  { this.visible = v; }
    public void toggleVisible()        { this.visible = !this.visible; }

    /**
     * Añade experiencia a la mascota y retorna cuántos niveles subió.
     * La fórmula se calcula externamente por el ExpManager.
     */
    public int addExp(long amount, int maxLevel, long base, long multiplier) {
        if (level >= maxLevel) return 0;
        exp += amount;
        int levelsGained = 0;
        while (level < maxLevel) {
            long required = base + ((long) level * multiplier);
            if (exp >= required) {
                exp -= required;
                level++;
                levelsGained++;
            } else {
                break;
            }
        }
        return levelsGained;
    }

    /** EXP requerida para el siguiente nivel con la fórmula base + nivel * mult */
    public long getExpRequired(long base, long multiplier) {
        return base + ((long) level * multiplier);
    }
}
