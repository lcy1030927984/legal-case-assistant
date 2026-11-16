package com.hubu.caseassistant.service.impl;

import com.hubu.caseassistant.config.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private FileStorageProperties fileStorageProperties;

    @InjectMocks
    private FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    @Test
    void calculateMd5_shouldReturnConsistentHashForSameContent() throws Exception {
        File file = tempDir.resolve("test.txt").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("hello world");
        }

        String hash1 = fileStorageService.calculateMd5(file);
        String hash2 = fileStorageService.calculateMd5(file);

        assertThat(hash1).isNotNull();
        assertThat(hash2).isEqualTo(hash1);
    }

    @Test
    void calculateMd5_shouldReturnDifferentHashForDifferentContent() throws Exception {
        File file1 = tempDir.resolve("a.txt").toFile();
        File file2 = tempDir.resolve("b.txt").toFile();
        try (FileWriter w1 = new FileWriter(file1)) { w1.write("hello"); }
        try (FileWriter w2 = new FileWriter(file2)) { w2.write("world"); }

        String hash1 = fileStorageService.calculateMd5(file1);
        String hash2 = fileStorageService.calculateMd5(file2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void calculateMd5_shouldReturnNullForNonExistentFile() {
        File nonExistent = new File(tempDir.toFile(), "does_not_exist.txt");
        String hash = fileStorageService.calculateMd5(nonExistent);
        assertThat(hash).isNull();
    }
}
