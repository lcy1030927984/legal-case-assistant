package com.hubu.caseassistant.service.impl;

import com.hubu.caseassistant.config.FileStorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageService {

    private final FileStorageProperties fileStorageProperties;

    @PostConstruct
    public void init() {
        try {
            Path rootLocation = Paths.get(fileStorageProperties.getStoragePath());
            if (Files.notExists(rootLocation)) {
                Files.createDirectories(rootLocation);
                log.info("成功创建了文件存储目录: {}", rootLocation);
            }
        } catch (IOException e) {
            log.error("无法创建文件存储目录", e);
            throw new RuntimeException("无法创建文件存储目录", e);
        }
    }

    public String calculateMd5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) != -1) {
                md.update(buffer, 0, length);
            }
            byte[] md5Bytes = md.digest();
            BigInteger bigInt = new BigInteger(1, md5Bytes);
            String hashtext = bigInt.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("计算文件 {} 的MD5哈希值失败，将跳过去重检查", file.getName(), e);
            return null;
        }
    }

    public File convertMultiPartToFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "tempfile";
        String uniqueFilename = System.currentTimeMillis() + "_" + originalFilename;
        File convFile = new File(fileStorageProperties.getStoragePath(), uniqueFilename);
        try (FileOutputStream fos = new FileOutputStream(convFile)) {
            fos.write(file.getBytes());
        }
        return convFile;
    }
}
