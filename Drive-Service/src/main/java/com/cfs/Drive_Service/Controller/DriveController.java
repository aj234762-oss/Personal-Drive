package com.cfs.Drive_Service.Controller;

import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.Service.DriveService;
import com.cfs.Drive_Service.Service.StorageQuotaService;
import com.cfs.Drive_Service.dto.FileDownload;
import com.cfs.Drive_Service.dto.FolderRequest;
import com.cfs.Drive_Service.dto.ItemPageResponse;
import com.cfs.Drive_Service.dto.FileVersionResponse;
import com.cfs.Drive_Service.Service.FileVersionService;
import com.cfs.Drive_Service.security.PreviewTicketService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class DriveController {

    private final DriveService drive;
    private final PreviewTicketService previewTickets;
    private final FileVersionService fileVersions;
    private final StorageQuotaService quota;

    public DriveController(DriveService drive, PreviewTicketService previewTickets, FileVersionService fileVersions, StorageQuotaService quota)
    {
        this.drive = drive;
        this.previewTickets = previewTickets;
        this.fileVersions = fileVersions;
        this.quota = quota;
    }

    @GetMapping("/quota")
    public StorageQuotaService.QuotaSummary quota(@AuthenticationPrincipal Jwt jwt) {
        return quota.summary(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public DriveItem folder(@Valid @RequestBody FolderRequest request, @AuthenticationPrincipal Jwt jwt){
        return drive.createFolder(request.name(),request.parentId(),UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/item")
    public ItemPageResponse list(@RequestParam(required = false) UUID parentId,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(defaultValue = "name") String sort,
                                 @RequestParam(defaultValue = "asc") String direction,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 @AuthenticationPrincipal Jwt jwt)
    {
        if (q != null && q.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search text must be 255 characters or fewer");
        String sortBy = switch (sort.toLowerCase(java.util.Locale.ROOT)) {
            case "name" -> "name";
            case "date", "createdat" -> "createdAt";
            case "size" -> "size";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sort must be name, date, or size");
        };
        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direction must be asc or desc");
        }
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be zero or greater");
        if (size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100");
        return ItemPageResponse.from(drive.list(parentId, UUID.fromString(jwt.getSubject()), q, sortBy, sortDirection, page, size));
    }

    @PostMapping(value = "/files",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DriveItem upload(@RequestPart("file") MultipartFile file,@RequestParam(required = false) UUID parentId, @AuthenticationPrincipal Jwt jwt)
    {
        return drive.upload(file,parentId,UUID.fromString(jwt.getSubject()));
    }

    @PutMapping(value = "/item/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DriveItem edit(@PathVariable UUID id, @RequestParam String name,
                          @RequestPart(value = "file", required = false) MultipartFile replacement,
                          @AuthenticationPrincipal Jwt jwt) {
        return drive.edit(id, name, replacement, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/files/{id}/versions")
    public java.util.List<FileVersionResponse> versions(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return fileVersions.history(id, UUID.fromString(jwt.getSubject())).stream().map(FileVersionResponse::from).toList();
    }

    @PostMapping("/files/{id}/versions/{versionId}/restore")
    public DriveItem restoreVersion(@PathVariable UUID id, @PathVariable UUID versionId, @AuthenticationPrincipal Jwt jwt) {
        return fileVersions.restore(id, versionId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) throws Exception {
        FileDownload download = drive.download(id, UUID.fromString(jwt.getSubject()));
        try{
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename(download.name(), StandardCharsets.UTF_8).build().toString())
                    .header("X-Content-Type-Options","nosniff")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(download.size()).body(download::writeTo);
        }catch (RuntimeException failure){
            download.close();
            throw failure;
        }
    }

    @PostMapping("/files/{id}/preview-ticket")
    public DriveService.PreviewLink createPreviewTicket(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return drive.createPreviewLink(id, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/files/{id}/preview")
    public ResponseEntity<StreamingResponseBody> preview(@PathVariable UUID id,
            @RequestParam String ticket,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        UUID ownerId = previewTickets.validate(ticket, id);
        FileDownload file = drive.preview(id, ownerId, range);
        feign.Response response = file.response();
        int status = response.status();
        if (status == 416) {
            ResponseEntity.BodyBuilder result = ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            header(response, HttpHeaders.CONTENT_RANGE).ifPresent(value -> result.header(HttpHeaders.CONTENT_RANGE, value));
            file.close();
            return result.build();
        }
        if (status != 200 && status != 206) {
            file.close();
            throw new ResponseStatusException(HttpStatus.valueOf(status), "Could not read preview content");
        }
        MediaType mediaType = MediaType.parseMediaType(drive.previewMediaType(id, ownerId));
        ResponseEntity.BodyBuilder result = ResponseEntity.status(status).contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Frame-Options", "SAMEORIGIN")
                .header("Content-Security-Policy", "frame-ancestors 'self'")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store");
        header(response, "Accept-Ranges").ifPresent(value -> result.header("Accept-Ranges", value));
        header(response, HttpHeaders.CONTENT_RANGE).ifPresent(value -> result.header(HttpHeaders.CONTENT_RANGE, value));
        header(response, HttpHeaders.CONTENT_LENGTH).ifPresent(value -> {
            try { result.contentLength(Long.parseLong(value)); } catch (NumberFormatException ignored) { }
        });
        return result.body(file::writeTo);
    }

    private static java.util.Optional<String> header(feign.Response response, String name) {
        return response.headers().entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream()).findFirst();
    }

    @DeleteMapping("/item/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt){
        drive.delete(id, UUID.fromString(jwt.getSubject()));
    }

}
