package com.cfs.Drive_Service.Model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DriveItemStatusConverter implements AttributeConverter<DriveItem.Status, String> {
    @Override
    public String convertToDatabaseColumn(DriveItem.Status status) {
        return status == null ? null : status.name();
    }

    @Override
    public DriveItem.Status convertToEntityAttribute(String status) {
        return status == null ? null : DriveItem.Status.valueOf(status);
    }
}
