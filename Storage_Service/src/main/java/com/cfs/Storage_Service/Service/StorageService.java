package com.cfs.Storage_Service.Service;

import com.cfs.Storage_Service.repo.DataStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class StorageService {
    private final DataStore store;

    public StorageService(DataStore store) {
        this.store = store;
    }
    public void put(UUID id, MultipartFile file) throws IOException
    {
        try(var input = file.getInputStream())
        {
            store.put(id,input);
        }
    }
    public Resource get(UUID id){
        return store.get(id);
    }

    public long size(UUID id) { return store.size(id); }

    public void copy(UUID sourceId, UUID destinationId) throws IOException {
        store.copy(sourceId, destinationId);
    }

    public InputStream openRange(UUID id, long start, long length) throws IOException {
        return store.openRange(id, start, length);
    }

    public void delete(UUID id) throws IOException
    {
        store.delete(id);
    }

}
