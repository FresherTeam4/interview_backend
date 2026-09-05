package com.baseProject.myBaseProject.dto.template;

import java.util.List;

public record TemplatePageResponse<T>(List<T> content, int page, int size, long totalElements) {
}

