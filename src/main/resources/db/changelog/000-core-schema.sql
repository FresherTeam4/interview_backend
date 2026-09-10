CREATE TABLE user_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NULL,
    google_id VARCHAR(255) NULL,
    avatar_url VARCHAR(1000) NULL,
    role VARCHAR(20) NOT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_accounts_email (email),
    UNIQUE KEY uq_user_accounts_google_id (google_id),
    CONSTRAINT chk_user_accounts_role CHECK (role IN ('ADMIN', 'USER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash CHAR(64) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_family_id (family_id),
    KEY idx_refresh_tokens_user_id (user_id),
    KEY idx_refresh_tokens_expires_at (expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cv_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    file_size_bytes BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    status_message TEXT NULL,
    is_active BIT(1) NOT NULL DEFAULT b'1',
    uploaded_at DATETIME(6) NOT NULL,
    parsed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_cv_documents_user_active (user_id, is_active),
    KEY idx_cv_documents_status (status),
    KEY idx_cv_documents_user_checksum (user_id, checksum_sha256),
    CONSTRAINT fk_cv_documents_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_cv_documents_status CHECK (
        status IN ('UPLOADED', 'PARSING', 'PARSED', 'FAILED')),
    CONSTRAINT chk_cv_documents_size CHECK (file_size_bytes >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE cv_parse_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cv_document_id BIGINT NOT NULL,
    raw_json JSON NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    duration_ms INT NULL,
    token_cost INT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cv_parse_results_document (cv_document_id),
    CONSTRAINT fk_cv_parse_results_document FOREIGN KEY (cv_document_id)
        REFERENCES cv_documents (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_cv_parse_results_metrics CHECK (
        (duration_ms IS NULL OR duration_ms >= 0)
        AND (token_cost IS NULL OR token_cost >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE candidate_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    cv_document_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    headline VARCHAR(255) NULL,
    summary TEXT NULL,
    years_experience DECIMAL(3, 1) NULL,
    target_position VARCHAR(150) NULL,
    seniority_level VARCHAR(30) NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'AUTO_PARSED',
    confirmed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_candidate_profiles_cv_document (cv_document_id),
    KEY idx_candidate_profiles_user_id (user_id),
    CONSTRAINT fk_candidate_profiles_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_candidate_profiles_cv_document FOREIGN KEY (cv_document_id)
        REFERENCES cv_documents (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_candidate_profiles_source CHECK (
        source IN ('AUTO_PARSED', 'USER_EDITED')),
    CONSTRAINT chk_candidate_profiles_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE profile_educations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    school VARCHAR(255) NOT NULL,
    degree VARCHAR(150) NULL,
    field_of_study VARCHAR(150) NULL,
    start_year SMALLINT NULL,
    end_year SMALLINT NULL,
    is_user_edited BIT(1) NOT NULL DEFAULT b'0',
    display_order SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_profile_educations_profile_id (profile_id),
    CONSTRAINT fk_profile_educations_profile FOREIGN KEY (profile_id)
        REFERENCES candidate_profiles (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE profile_skills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    category VARCHAR(50) NULL,
    is_user_edited BIT(1) NOT NULL DEFAULT b'0',
    display_order SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_profile_skills_profile_name (profile_id, name),
    CONSTRAINT fk_profile_skills_profile FOREIGN KEY (profile_id)
        REFERENCES candidate_profiles (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE profile_projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    role_in_project VARCHAR(150) NULL,
    tech_stack TEXT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    is_user_edited BIT(1) NOT NULL DEFAULT b'0',
    display_order SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_profile_projects_profile_id (profile_id),
    CONSTRAINT fk_profile_projects_profile FOREIGN KEY (profile_id)
        REFERENCES candidate_profiles (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
