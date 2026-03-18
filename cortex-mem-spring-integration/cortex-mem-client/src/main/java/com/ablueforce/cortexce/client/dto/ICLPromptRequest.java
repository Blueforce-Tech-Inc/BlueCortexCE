package com.ablueforce.cortexce.client.dto;

public record ICLPromptRequest(
    String task,
    String project
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String task;
        private String project;

        public Builder task(String task) { this.task = task; return this; }
        public Builder project(String project) { this.project = project; return this; }

        public ICLPromptRequest build() {
            return new ICLPromptRequest(task, project);
        }
    }
}
