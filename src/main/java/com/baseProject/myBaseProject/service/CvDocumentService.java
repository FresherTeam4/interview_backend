package com.baseProject.myBaseProject.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.baseProject.myBaseProject.dto.cv.CvDocumentResponse;

public interface CvDocumentService {

    /** US-1: nhận file PDF, lưu file và tạo bản ghi cv_documents ở trạng thái UPLOADED. */
    CvDocumentResponse upload(Long userId, MultipartFile file);

    List<CvDocumentResponse> list(Long userId);

    CvDocumentResponse activeCv(Long userId);

    /** US-2: đọc CV bằng AI rồi dựng hồ sơ ứng viên. */
    CvDocumentResponse parse(Long userId, Long cvDocumentId);
}
