-- V1: baseline schema. Applied only to EMPTY databases (local, CI, new environments).
-- The existing Neon database was created manually and is baselined at version 1 instead,
-- so this script is never executed against it. See docs/DATABASE_MIGRATIONS.md.

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    platform_role VARCHAR(20)  NOT NULL DEFAULT 'USER',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_users_platform_role CHECK (platform_role IN ('USER', 'ADMIN'))
);

CREATE TABLE projects (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    project_key VARCHAR(10)  NOT NULL UNIQUE,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    owner_id    UUID         NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_projects_status CHECK (status IN ('ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED'))
);

CREATE TABLE project_members (
    project_id UUID        NOT NULL REFERENCES projects (id),
    user_id    UUID        NOT NULL REFERENCES users (id),
    role       VARCHAR(20) NOT NULL,
    joined_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT chk_project_members_role CHECK (role IN ('OWNER', 'MANAGER', 'MEMBER', 'VIEWER'))
);

CREATE TABLE issues (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID          NOT NULL REFERENCES projects (id),
    issue_number    INTEGER       NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    description     TEXT,
    issue_type      VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'TODO',
    priority        VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM',
    reporter_id     UUID          NOT NULL REFERENCES users (id),
    due_date        DATE,
    estimated_hours NUMERIC(8, 2),
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_issues_project_number UNIQUE (project_id, issue_number),
    CONSTRAINT uq_issues_id_project UNIQUE (id, project_id),
    CONSTRAINT chk_issues_number_positive CHECK (issue_number > 0),
    CONSTRAINT chk_issues_hours_nonnegative CHECK (estimated_hours IS NULL OR estimated_hours >= 0),
    CONSTRAINT chk_issues_type CHECK (issue_type IN ('TASK', 'BUG', 'STORY', 'EPIC')),
    CONSTRAINT chk_issues_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE', 'CANCELLED')),
    CONSTRAINT chk_issues_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE TABLE issue_comments (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id   UUID        NOT NULL REFERENCES issues (id),
    author_id  UUID        NOT NULL REFERENCES users (id),
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_issue_comments_content CHECK (length(btrim(content)) > 0)
);

CREATE TABLE issue_assignees (
    issue_id    UUID        NOT NULL REFERENCES issues (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (issue_id, user_id)
);

CREATE TABLE labels (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID        NOT NULL REFERENCES projects (id),
    name       VARCHAR(50) NOT NULL,
    color      VARCHAR(7)  NOT NULL DEFAULT '#808080',
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_labels_project_name UNIQUE (project_id, name),
    CONSTRAINT uq_labels_id_project UNIQUE (id, project_id),
    CONSTRAINT chk_labels_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE TABLE issue_labels (
    issue_id   UUID NOT NULL,
    label_id   UUID NOT NULL,
    project_id UUID NOT NULL,
    PRIMARY KEY (issue_id, label_id),
    CONSTRAINT fk_issue_labels_issue FOREIGN KEY (issue_id, project_id)
        REFERENCES issues (id, project_id) ON DELETE CASCADE,
    CONSTRAINT fk_issue_labels_label FOREIGN KEY (label_id, project_id)
        REFERENCES labels (id, project_id) ON DELETE CASCADE
);

CREATE TABLE issue_history (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id   UUID        NOT NULL REFERENCES issues (id),
    changed_by UUID        REFERENCES users (id) ON DELETE SET NULL,
    field_name VARCHAR(50) NOT NULL,
    old_value  TEXT,
    new_value  TEXT,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_projects_owner_id ON projects (owner_id);
CREATE INDEX idx_project_members_user_id ON project_members (user_id);
CREATE INDEX idx_issues_project_status ON issues (project_id, status);
CREATE INDEX idx_issues_reporter_id ON issues (reporter_id);
CREATE INDEX idx_issues_due_date ON issues (due_date);
CREATE INDEX idx_issue_comments_issue_id ON issue_comments (issue_id);
CREATE INDEX idx_issue_assignees_user_id ON issue_assignees (user_id);
CREATE INDEX idx_labels_project_id ON labels (project_id);
CREATE INDEX idx_issue_labels_label_id ON issue_labels (label_id);
CREATE INDEX idx_issue_history_issue_id ON issue_history (issue_id, changed_at);

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_projects_updated_at BEFORE UPDATE ON projects
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_issues_updated_at BEFORE UPDATE ON issues
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_issue_comments_updated_at BEFORE UPDATE ON issue_comments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
