package com.rostra.auction.support;

import com.rostra.auction.storage.ImageStorage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Stands in for the object store in tests that are about the API rules, not about S3. */
public class InMemoryImageStorage implements ImageStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final Map<String, String> contentTypes = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] data, String contentType) {
        objects.put(key, data);
        contentTypes.put(key, contentType);
    }

    @Override
    public byte[] get(String key) {
        return objects.get(key);
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
        contentTypes.remove(key);
    }

    /** How many objects are stored under a key prefix, e.g. "auctions/{auctionId}/". */
    public long countUnder(String prefix) {
        return objects.keySet().stream().filter(k -> k.startsWith(prefix)).count();
    }

    public String contentTypeUnder(String prefix) {
        return contentTypes.entrySet().stream().filter(e -> e.getKey().startsWith(prefix)).map(Map.Entry::getValue).findFirst().orElse(null);
    }
}
