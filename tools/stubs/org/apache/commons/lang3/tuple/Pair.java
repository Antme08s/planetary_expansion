package org.apache.commons.lang3.tuple;

/**
 * Stub SOLO para compilar (commons-lang3 no está en la carpeta libraries de CurseForge).
 * No se empaqueta en el jar: en el juego se usa la clase real que trae Minecraft.
 */
public abstract class Pair<L, R> implements java.util.Map.Entry<L, R> {
    public abstract L getLeft();
    public abstract R getRight();
    public final L getKey() { return getLeft(); }
    public R getValue() { return getRight(); }
}
