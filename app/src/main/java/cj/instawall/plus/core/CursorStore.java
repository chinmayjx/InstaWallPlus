package cj.instawall.plus.core;

/** Where an interrupted sync remembers how far it got. */
public interface CursorStore {
    String get(String key);

    void put(String key, String cursor);

    void remove(String key);
}
