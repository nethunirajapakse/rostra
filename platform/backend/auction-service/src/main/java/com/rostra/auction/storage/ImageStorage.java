package com.rostra.auction.storage;

/** Where image bytes live. The database only keeps the object key. */
public interface ImageStorage {

    void put(String key, byte[] data, String contentType);

    /** @return the bytes, or null when no such object exists */
    byte[] get(String key);

    /** Best effort: a failure is logged, never thrown, because a leftover object is harmless. */
    void delete(String key);
}
