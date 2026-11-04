package com.ablueforce.cortexce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the retention cleanup job.
 *
 * <p>Every switch defaults to the safe side: the job is disabled, it runs in
 * dry-run mode when enabled, and every retention window is {@code 0}, which means
 * "keep forever". A deployment has to opt in explicitly before a single row or
 * log file is removed.
 *
 * <p>Windows are independent per table so an operator can keep prompts longer
 * than observations, or age out logs without touching the database.
 */
@Component
@ConfigurationProperties(prefix = "cortexce.retention")
public class RetentionProperties {

    /** Master switch. When false the scheduled job returns immediately. */
    private boolean enabled = false;

    /** Cron for the scheduled run; only consulted when {@code enabled} is true. */
    private String cron = "0 0 3 * * *";

    /**
     * When true the job only counts what it would delete and logs the counts.
     * Flip to false to actually delete. Default true on purpose.
     */
    private boolean dryRun = true;

    /** Keep user prompts for this many days; 0 disables prompt cleanup. */
    private int promptsDays = 0;

    /** Keep observations for this many days; 0 disables observation cleanup. */
    private int observationsDays = 0;

    /** Keep summaries for this many days; 0 disables summary cleanup. */
    private int summariesDays = 0;

    /** Keep rolled log files for this many days; 0 disables log cleanup. */
    private int logsDays = 0;

    /**
     * Optional project scope. Blank means every project. When set, the database
     * cleanup only touches rows whose {@code project_path} matches exactly.
     */
    private String project = "";

    /** Directory holding rolled log files; blank falls back to {@code LOG_DIR} or ~/.claude-mem/logs. */
    private String logDir = "";

    /** Only files whose name starts with this prefix are eligible for log cleanup. */
    private String logFilePrefix = "claude-mem";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public int getPromptsDays() { return promptsDays; }
    public void setPromptsDays(int promptsDays) { this.promptsDays = promptsDays; }

    public int getObservationsDays() { return observationsDays; }
    public void setObservationsDays(int observationsDays) { this.observationsDays = observationsDays; }

    public int getSummariesDays() { return summariesDays; }
    public void setSummariesDays(int summariesDays) { this.summariesDays = summariesDays; }

    public int getLogsDays() { return logsDays; }
    public void setLogsDays(int logsDays) { this.logsDays = logsDays; }

    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }

    public String getLogDir() { return logDir; }
    public void setLogDir(String logDir) { this.logDir = logDir; }

    public String getLogFilePrefix() { return logFilePrefix; }
    public void setLogFilePrefix(String logFilePrefix) { this.logFilePrefix = logFilePrefix; }

    /** Resolved log directory: explicit value, else LOG_DIR, else the default install path. */
    public String resolvedLogDir() {
        if (logDir != null && !logDir.isBlank()) {
            return logDir;
        }
        String env = System.getenv("LOG_DIR");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return System.getProperty("user.home") + "/.claude-mem/logs";
    }
}
