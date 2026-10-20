package com.cfs.Drive_Service.dto;

import com.cfs.Drive_Service.Model.DriveItem;
import org.springframework.data.domain.Page;

import java.util.List;

public record ItemPageResponse(
        List<DriveItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static ItemPageResponse from(Page<DriveItem> result) {
        return new ItemPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
