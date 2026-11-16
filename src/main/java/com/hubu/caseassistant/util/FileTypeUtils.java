package com.hubu.caseassistant.util;

import org.springframework.http.MediaType;

/**
 * 文件类型工具类，根据文件扩展名确定 MIME 类型。
 * 统一替代散落在各处的 duplicate 判断逻辑。
 */
public final class FileTypeUtils {

    private FileTypeUtils() {}

    public static MediaType fromFilename(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".docx")) {
            return MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        if (lower.endsWith(".doc")) {
            return MediaType.valueOf("application/msword");
        }
        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
