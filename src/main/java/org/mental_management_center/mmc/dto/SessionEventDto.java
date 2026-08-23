package org.mental_management_center.mmc.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionEventDto {
    private String id;
    private String title;
    private String start; // Очікується формат ISO 8601 (напр. 2026-08-23T10:00:00)
    private String end;
    private String color; // Колір події для візуального відображення статусу
    private String description;
}