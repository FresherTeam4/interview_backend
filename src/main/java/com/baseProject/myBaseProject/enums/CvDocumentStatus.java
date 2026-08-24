package com.baseProject.myBaseProject.enums;

/**
 * Lifecycle of an uploaded CV file. Enforced in the database by
 * {@code chk_cv_documents_status}.
 */
public enum CvDocumentStatus {
    /** Just uploaded, not processed yet. */
    UPLOADED,
    /** Parsing in progress. */
    PARSING,
    /** Parsed successfully. */
    PARSED,
    /** Broken file or parse error; see {@code status_message}. */
    FAILED
}
