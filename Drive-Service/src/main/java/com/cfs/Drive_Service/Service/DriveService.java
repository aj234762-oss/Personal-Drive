package com.cfs.Drive_Service.Service;

import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.client.StorageClient;
import com.cfs.Drive_Service.dto.FileDownload;
import com.cfs.Drive_Service.security.PreviewTicketService;
import feign.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import jakarta.transaction.Transactional;

@Service
public class DriveService {

    private static final Logger log = LoggerFactory.getLogger(DriveService.class);

    private final ItemService itemService;
    private final StorageClient storageClient;
    private final PreviewTicketService previewTickets;
    private final FileVersionService fileVersions;
    private final StorageQuotaService quota;


    public DriveService(ItemService itemService, StorageClient storageClient, PreviewTicketService previewTickets,
                        FileVersionService fileVersions, StorageQuotaService quota) {
        this.itemService = itemService;
        this.storageClient = storageClient;
        this.previewTickets = previewTickets;
        this.fileVersions = fileVersions;
        this.quota = quota;
    }
    public DriveItem createFolder(String name, UUID parentID, UUID ownerId)
    {
        return itemService.create(name,parentID,DriveItem.Kind.FOLDER,0,null,ownerId);
    }

    public Page<DriveItem> list(UUID parentId, UUID ownerId, String query, String sortBy,
                                Sort.Direction direction, int page, int size){
        return itemService.list(parentId, ownerId, query, sortBy, direction, page, size);
    }
    @Transactional
    public DriveItem upload(MultipartFile file,UUID parentID,UUID ownerId){
        quota.ensureCanAdd(ownerId, file.getSize());
        DriveItem item = itemService.create(file.getOriginalFilename(),parentID,DriveItem.Kind.FILE,file.getSize(),file.getContentType(),ownerId);
        try{
            storageClient.put(item.getId(),file);
            return itemService.ready(item.getId(), ownerId);
        }catch (RuntimeException e){
            try {
                storageClient.delete(item.getId());
                itemService.remove(item.getId());
            }catch (RuntimeException cleanUpFailure){
                log.warn("Pending Upload {} needs cleanup",item.getId(),cleanUpFailure);
            }
            throw e;
        }
    }

    @Transactional
    public DriveItem edit(UUID id, String name, MultipartFile replacement, UUID actorId) {
        DriveItem item = itemService.getForEdit(id, actorId);
        ItemService.validateName(name);
        if (replacement != null) {
            if (item.getKind() != DriveItem.Kind.FILE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A folder cannot have file contents");
            quota.ensureCanAdd(item.getOwnerId(), replacement.getSize());
            fileVersions.snapshotBeforeReplace(item, actorId);
            storageClient.put(id, replacement);
            return itemService.edit(id, actorId, name, replacement.getSize(), replacement.getContentType());
        }
        return itemService.edit(id, actorId, name, null, null);
    }


    public FileDownload download(UUID id, UUID ownerId)
    {
        DriveItem item = itemService.get(id, ownerId);
        if(item.getKind()!=DriveItem.Kind.FILE)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A folder has no content");
        }
        Response response = storageClient.get(id, null);
        return new FileDownload(item.getName(),item.getSize(),response);
    }

    public PreviewLink createPreviewLink(UUID id, UUID ownerId) {
        DriveItem item = itemService.get(id, ownerId);
        requirePreviewableFile(item);
        PreviewTicketService.Ticket ticket = previewTickets.issue(id, ownerId);
        return new PreviewLink("/api/files/" + id + "/preview?ticket=" + ticket.token(), ticket.expiresIn());
    }

    public FileDownload preview(UUID id, UUID ownerId, String range) {
        DriveItem item = itemService.get(id, ownerId);
        requirePreviewableFile(item);
        return new FileDownload(item.getName(), item.getSize(), storageClient.get(id, range));
    }

    public String previewMediaType(UUID id, UUID ownerId) {
        DriveItem item = itemService.get(id, ownerId);
        requirePreviewableFile(item);
        return MediaType.parseMediaType(item.getMediaType()).toString();
    }

    private static void requirePreviewableFile(DriveItem item) {
        if (item.getKind() != DriveItem.Kind.FILE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A folder cannot be previewed");
        String value = item.getMediaType();
        if (value == null) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This file type cannot be previewed");
        try {
            MediaType type = MediaType.parseMediaType(value);
            boolean safeImage = type.getType().equals("image") && java.util.Set.of("png", "jpeg", "gif", "webp", "avif", "bmp").contains(type.getSubtype());
            boolean pdf = type.getType().equals("application") && type.getSubtype().equals("pdf");
            boolean media = (type.getType().equals("audio") || type.getType().equals("video")) && !type.getSubtype().equals("*");
            if (!safeImage && !pdf && !media)
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This file type cannot be previewed");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This file type cannot be previewed");
        }
    }

    public record PreviewLink(String url, long expiresIn) { }

    public void delete(UUID id, UUID ownerId){
        itemService.trash(id, ownerId);
    }

}
