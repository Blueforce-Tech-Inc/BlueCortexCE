package com.ablueforce.cortexce.client.dto;

public record ExperienceRequest(
    String task,
    String project,
    Integer count
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String task;
        private String project;
        private Integer count = 4;

        public Builder task(String task) { this.task = task; return this; }
        public Builder project(String project) { this.project = project; return this; }
        public Builder count(Integer count) { this.count = count; return this; }

        public ExperienceRequest build() {
            return new ExperienceRequest(task, project, count);
        }
    }
}
