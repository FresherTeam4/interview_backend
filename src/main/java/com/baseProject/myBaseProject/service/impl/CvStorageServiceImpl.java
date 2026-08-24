package com.baseProject.myBaseProject.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.baseProject.myBaseProject.config.properites.CvStorageProperties;
import com.baseProject.myBaseProject.exception.CvStorageException;
import com.baseProject.myBaseProject.service.CvStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lưu CV xuống đĩa của server, mỗi user một thư mục riêng. Đủ dùng cho hệ thống hiện tại;
 * khi cần S3 thì viết implementation khác của {@link CvStorageService}, không phải sửa service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CvStorageServiceImpl implements CvStorageService {
    private final CvStorageProperties properties;

    @Override
    public String store(Long userId, MultipartFile file) {
        String storageKey = "user-%d/%s.pdf".formatted(userId, UUID.randomUUID());
        Path target = resolve(storageKey);
        try (InputStream input = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("Không ghi được file CV {}", target, ex);
            throw new CvStorageException();
        }
        return storageKey;
    }

    @Override
    public byte[] read(String storageKey) {
        Path source = resolve(storageKey);
        try {
            return Files.readAllBytes(source);
        } catch (IOException ex) {
            log.error("Không đọc được file CV {}", source, ex);
            throw new CvStorageException();
        }
    }

    /** Chặn storage key dạng "../" đi ra ngoài thư mục cấu hình. */
    private Path resolve(String storageKey) {
        Path base = Path.of(properties.dir()).toAbsolutePath().normalize();
        Path resolved = base.resolve(storageKey).normalize();
        if (!resolved.startsWith(base)) {
            throw new CvStorageException();
        }
        return resolved;
    }
}
