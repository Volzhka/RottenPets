package com.betterpets.data;

import org.bukkit.entity.EntityType;

/**
 * Enumeración de todos los tipos de mascotas disponibles.
 * La clave (key) se usa para buscar configuración en pets.yml y guardar datos.
 */
public enum PetType {

    HORMIGA("hormiga", EntityType.SILVERFISH),
    AJOLOTE("ajolote", EntityType.AXOLOTL),
    DRAGON_AZUL("dragon_azul", EntityType.PHANTOM),
    GATO("gato", EntityType.CAT),
    GALLINA("gallina", EntityType.CHICKEN),
    PERRO("perro", EntityType.WOLF),
    DELFIN("delfin", EntityType.DOLPHIN),
    ELDER_GUARDIAN("elder_guardian", EntityType.ELDER_GUARDIAN),
    GHAST("ghast", EntityType.GHAST),
    HAMSTER("hamster", EntityType.RABBIT),
    HEROBRINE("herobrine", EntityType.ZOMBIE),
    BUHO("buho", EntityType.PARROT),
    PANDA("panda", EntityType.PANDA),
    PINGUINO("pinguino", EntityType.SNOWMAN),
    DRAGON_ROJO("dragon_rojo", EntityType.BLAZE),
    LORO_ROJO("loro_rojo", EntityType.PARROT),
    FENIX("fenix", EntityType.BLAZE),
    PEZ_GLOBO("pez_globo", EntityType.PUFFERFISH),
    CONEJO("conejo", EntityType.RABBIT),
    SEGADOR("segador", EntityType.ENDERMAN),
    CARACOL("caracol", EntityType.SILVERFISH),
    ESPINOSAURIO("espinosaurio", EntityType.RAVAGER),
    TIGRE("tigre", EntityType.CAT),
    TORTUGA("tortuga", EntityType.TURTLE),
    GUARDIAN("guardian", EntityType.WARDEN),
    GUSANO("gusano", EntityType.ENDERMITE);

    private final String key;
    private final EntityType defaultEntityType;

    PetType(String key, EntityType defaultEntityType) {
        this.key = key;
        this.defaultEntityType = defaultEntityType;
    }

    /** Clave de configuración (usada en pets.yml y archivos de jugador). */
    public String getKey() { return key; }

    /** Tipo de entidad por defecto (puede sobreescribirse en pets.yml). */
    public EntityType getDefaultEntityType() { return defaultEntityType; }

    /**
     * Busca un PetType por su clave de configuración.
     * @return el PetType, o null si no se encuentra.
     */
    public static PetType fromKey(String key) {
        if (key == null) return null;
        for (PetType type : values()) {
            if (type.key.equalsIgnoreCase(key)) return type;
        }
        return null;
    }
}
