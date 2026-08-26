package com.baseProject.myBaseProject.cv.validation;

import org.springframework.web.multipart.MultipartFile;


public interface CvFileValidator {
    byte[] validateAndRead(MultipartFile file);
}
