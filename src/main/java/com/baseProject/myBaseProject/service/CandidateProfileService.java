package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.cv.CvParseOutcome;
import com.baseProject.myBaseProject.dto.profile.CandidateProfileResponse;
import com.baseProject.myBaseProject.dto.profile.UpdateCandidateProfileRequest;

public interface CandidateProfileService {

    /** US-3: xem hồ sơ AI đã bóc tách. */
    CandidateProfileResponse currentProfile(Long userId);

    /** US-3: người dùng sửa lại thông tin AI bóc tách sai. */
    CandidateProfileResponse update(Long userId, UpdateCandidateProfileRequest request);

    /** US-3: xác nhận hồ sơ đúng, đây là điều kiện để bắt đầu buổi phỏng vấn. */
    CandidateProfileResponse confirm(Long userId);

    /**
     * US-2: ghi kết quả AI đọc CV thành hồ sơ. Gộp trong một transaction để hồ sơ và
     * bảng cv_parse_results không bao giờ lệch nhau.
     */
    void applyParseResult(Long cvDocumentId, CvParseOutcome outcome);
}
