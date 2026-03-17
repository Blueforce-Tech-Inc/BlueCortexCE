-- P2-2: UserPromptEntity project field - adds project tracking to user prompts
-- Enables filtering prompts by project in the Viewer UI

ALTER TABLE mem_user_prompts ADD COLUMN IF NOT EXISTS project_path TEXT;
-- Create index for faster project-based queries
CREATE INDEX IF NOT EXISTS idx_mem_user_prompts_project ON mem_user_prompts(project_path);
