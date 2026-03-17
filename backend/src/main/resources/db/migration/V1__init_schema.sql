CREATE EXTENSION IF NOT EXISTS vector;

-- 1. Sessions table (maps SDKSession)
CREATE TABLE mem_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_session_id VARCHAR(255) UNIQUE NOT NULL,
    memory_session_id VARCHAR(255) UNIQUE,
    project_path TEXT NOT NULL,
    user_prompt TEXT,
    last_assistant_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    started_at_epoch BIGINT NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    completed_at_epoch BIGINT,
    status VARCHAR(50) DEFAULT 'active'
);

CREATE INDEX idx_sessions_content_session ON mem_sessions(content_session_id);
CREATE INDEX idx_sessions_memory_session ON mem_sessions(memory_session_id);
CREATE INDEX idx_sessions_project ON mem_sessions(project_path);
CREATE INDEX idx_sessions_status ON mem_sessions(status);
CREATE INDEX idx_sessions_started ON mem_sessions(started_at_epoch DESC);

-- 2. Observations table (maps Observation)
CREATE TABLE mem_observations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memory_session_id VARCHAR(255) NOT NULL REFERENCES mem_sessions(memory_session_id),
    project_path TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    title TEXT,
    subtitle TEXT,
    content TEXT,
    facts JSONB,
    concepts JSONB,
    files_read JSONB,
    files_modified JSONB,
    discovery_tokens INT DEFAULT 0,
    prompt_number INT,
    embedding vector(768),
    search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(content, '')), 'B')
    ) STORED,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at_epoch BIGINT NOT NULL
);

-- 3. Summaries table (maps SessionSummary)
CREATE TABLE mem_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memory_session_id VARCHAR(255) NOT NULL REFERENCES mem_sessions(memory_session_id),
    project_path TEXT NOT NULL,
    request TEXT,
    investigated TEXT,
    learned TEXT,
    completed TEXT,
    next_steps TEXT,
    files_read TEXT,
    files_edited TEXT,
    notes TEXT,
    prompt_number INT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at_epoch BIGINT NOT NULL
);

-- 4. User Prompts table (maps UserPrompt)
CREATE TABLE mem_user_prompts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_session_id VARCHAR(255) NOT NULL REFERENCES mem_sessions(content_session_id),
    prompt_number INT NOT NULL,
    prompt_text TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at_epoch BIGINT NOT NULL
);

-- 5. Pending Messages table (message queue persistence for crash recovery)
CREATE TABLE mem_pending_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_db_id UUID NOT NULL,
    content_session_id VARCHAR(255) NOT NULL,
    message_type VARCHAR(50) NOT NULL CHECK (message_type IN ('observation', 'summarize')),
    tool_name TEXT,
    tool_input TEXT,
    tool_response TEXT,
    cwd TEXT,
    last_user_message TEXT,
    last_assistant_message TEXT,
    prompt_number INT,
    status VARCHAR(50) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'processing', 'processed', 'failed')),
    retry_count INT NOT NULL DEFAULT 0,
    created_at_epoch BIGINT NOT NULL,
    started_processing_at_epoch BIGINT,
    completed_at_epoch BIGINT,
    failed_at_epoch BIGINT,
    FOREIGN KEY (session_db_id) REFERENCES mem_sessions(id) ON DELETE CASCADE
);

-- 6. Indexes
CREATE INDEX idx_obs_project ON mem_observations(project_path);
CREATE INDEX idx_obs_type ON mem_observations(type);
CREATE INDEX idx_obs_created_epoch ON mem_observations(created_at_epoch DESC);
CREATE INDEX idx_obs_search ON mem_observations USING GIN(search_vector);
CREATE INDEX idx_obs_embedding ON mem_observations USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_obs_facts_gin ON mem_observations USING GIN (facts jsonb_path_ops);
CREATE INDEX idx_obs_concepts_gin ON mem_observations USING GIN (concepts jsonb_path_ops);

CREATE INDEX idx_summaries_project ON mem_summaries(project_path);
CREATE INDEX idx_summaries_session ON mem_summaries(memory_session_id);
CREATE INDEX idx_summaries_created ON mem_summaries(created_at_epoch DESC);

CREATE INDEX idx_prompts_content_session ON mem_user_prompts(content_session_id);
CREATE INDEX idx_prompts_created ON mem_user_prompts(created_at_epoch DESC);
CREATE INDEX idx_prompts_prompt_number ON mem_user_prompts(prompt_number);
CREATE INDEX idx_prompts_lookup ON mem_user_prompts(content_session_id, prompt_number);

CREATE INDEX idx_pending_messages_session ON mem_pending_messages(session_db_id);
CREATE INDEX idx_pending_messages_status ON mem_pending_messages(status);
CREATE INDEX idx_pending_messages_content_session ON mem_pending_messages(content_session_id);
