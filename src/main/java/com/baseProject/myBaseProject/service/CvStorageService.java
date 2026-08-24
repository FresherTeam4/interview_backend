package com.baseProject.myBaseProject.service;

import org.springframework.web.multipart.MultipartFile;

/** Lưu và đọc file CV gốc. Tách riêng để sau này đổi sang S3/MinIO chỉ cần thay implementation. */
public interface CvStorageService {

    /** @return storage key tương đối, được lưu vào {@code cv_documents.storage_key} */
    String store(Long userId, MultipartFile file);

    byte[] read(String storageKey);
}
