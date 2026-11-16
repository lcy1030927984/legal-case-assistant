package com.hubu.caseassistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DifyFileUploadResponse {
    private String id;
    private String name;
    private long size;
    @com.fasterxml.jackson.annotation.JsonProperty("mime_type")
    private String mimeType;
}