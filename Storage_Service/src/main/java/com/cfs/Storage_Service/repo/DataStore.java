package com.cfs.Storage_Service.repo;

import com.cfs.Storage_Service.Config.StorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Repository
public class DataStore {
    // nio path
    private final Path root;
// File read
    public DataStore(StorageProperties properties) throws IOException {
        this.root=Path.of(properties.directory()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public void put(UUID id, InputStream input) throws IOException
    {
        Path temporary = Files.createTempFile(root,"Upload-","temp");
        try{
            Files.copy(input,temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary,root.resolve(id.toString()),StandardCopyOption.REPLACE_EXISTING);
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }
    // spring.io resource
    public Resource get(UUID id)
    {
        Path path=root.resolve(id.toString());
        if(!Files.isRegularFile(path)){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Content not found");
        }
        return new FileSystemResource(path);
    }

    public long size(UUID id) {
        Path path = objectPath(id);
        try { return Files.size(path); }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"); }
    }

    public void copy(UUID sourceId, UUID destinationId) throws IOException {
        Path source = objectPath(sourceId);
        Path destination = root.resolve(destinationId.toString());
        Path temporary = Files.createTempFile(root, "Version-", ".temp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public InputStream openRange(UUID id, long start, long length) throws IOException {
        InputStream input = Files.newInputStream(objectPath(id));
        try {
            input.skipNBytes(start);
            return new FilterInputStream(input) {
                private long remaining = length;
                @Override public int read() throws IOException {
                    if (remaining <= 0) return -1;
                    int value = super.read();
                    if (value >= 0) remaining--;
                    return value;
                }
                @Override public int read(byte[] bytes, int offset, int count) throws IOException {
                    if (remaining <= 0) return -1;
                    int read = super.read(bytes, offset, (int)Math.min(count, remaining));
                    if (read > 0) remaining -= read;
                    return read;
                }
            };
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }

    private Path objectPath(UUID id) {
        Path path = root.resolve(id.toString());
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        return path;
    }

    public void delete(UUID id) throws IOException
    {
        Files.deleteIfExists(root.resolve(id.toString()));
    }

}
