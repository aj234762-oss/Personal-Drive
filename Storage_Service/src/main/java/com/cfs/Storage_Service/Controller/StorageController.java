package com.cfs.Storage_Service.Controller;

import com.cfs.Storage_Service.Service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

@RestController
@RequestMapping("/internal/objects")
public class StorageController {
    private final StorageService service;

    public StorageController(StorageService service) {
        this.service = service;
    }

    @PutMapping(value = "/{id}",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> put(@PathVariable UUID id, @RequestPart("file")MultipartFile file) throws IOException{
        service.put(id,file);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{sourceId}/copy/{destinationId}")
    public ResponseEntity<Void> copy(@PathVariable UUID sourceId, @PathVariable UUID destinationId) throws IOException {
        if (sourceId.equals(destinationId)) return ResponseEntity.badRequest().build();
        service.copy(sourceId, destinationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<StreamingResponseBody> get(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.RANGE, required = false) String range)
    {
        long size = service.size(id);
        if (range == null || range.isBlank()) {
            Resource resource = service.get(id);
            StreamingResponseBody body = output -> {
                try (InputStream input = resource.getInputStream()) { input.transferTo(output); }
            };
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("Accept-Ranges", "bytes").contentLength(size).body(body);
        }

        long[] bounds = parseRange(range, size);
        if (bounds == null) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + size).build();
        }
        long start = bounds[0], end = bounds[1], length = end - start + 1;
        StreamingResponseBody body = output -> {
            try (InputStream input = service.openRange(id, start, length)) { input.transferTo(output); }
        };
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Accept-Ranges", "bytes").header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + size)
                .contentLength(length).body(body);
    }

    private static long[] parseRange(String header, long size) {
        if (size <= 0 || header.indexOf(',') >= 0) return null;
        Matcher matcher = Pattern.compile("bytes=(\\d*)-(\\d*)").matcher(header.trim());
        if (!matcher.matches() || (matcher.group(1).isEmpty() && matcher.group(2).isEmpty())) return null;
        try {
            long start, end;
            if (matcher.group(1).isEmpty()) {
                long suffix = Long.parseLong(matcher.group(2));
                if (suffix <= 0) return null;
                start = Math.max(0, size - suffix); end = size - 1;
            } else {
                start = Long.parseLong(matcher.group(1));
                end = matcher.group(2).isEmpty() ? size - 1 : Math.min(Long.parseLong(matcher.group(2)), size - 1);
            }
            return start >= size || end < start ? null : new long[]{start, end};
        } catch (NumberFormatException exception) { return null; }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) throws IOException
    {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

}
