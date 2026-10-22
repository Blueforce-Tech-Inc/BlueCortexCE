package com.ablueforce.cortexce.migration;

import com.ablueforce.cortexce.migration.ConversationScopeBackfillRunner.RunSummary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Operator entry point for the Conversation / Memory Scope backfill.
 *
 * <p>Run explicitly, once, on one instance — never from application startup. The default is a
 * dry run; writing requires {@code --apply}.
 *
 * <pre>
 *   # dry run (default): reads everything, writes nothing, prints what would change
 *   java -cp cortex-ce.jar com.ablueforce.cortexce.migration.BackfillCliMain \
 *        --url jdbc:postgresql://host:5432/db --user postgres [--password ...]
 *
 *   # apply
 *   ... --apply [--accept-sentinel-mapping] [--batch-size 500]
 * </pre>
 *
 * <p>{@code --accept-sentinel-mapping} is the explicit, reviewed release of the
 * {@code manual-memories} identity ambiguity. Without it, manual buckets are reported as
 * {@code LEGACY_SENTINEL_IDENTITY_AMBIGUITY} and left untouched.
 *
 * <p>This class deliberately does not read the application's datasource configuration: an
 * operator must name the target database explicitly so a stray environment variable cannot
 * silently point a migration at the wrong server.
 */
public final class BackfillCliMain {

    private BackfillCliMain() {
    }

    public static void main(String[] args) {
        String url = null;
        String user = "postgres";
        String password = "";
        int batchSize = 500;
        boolean apply = false;
        boolean acceptSentinel = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--url" -> url = requireValue(args, ++i, "--url");
                case "--user" -> user = requireValue(args, ++i, "--user");
                case "--password" -> password = requireValue(args, ++i, "--password");
                case "--batch-size" -> batchSize = Integer.parseInt(requireValue(args, ++i, "--batch-size"));
                case "--apply" -> apply = true;
                case "--accept-sentinel-mapping" -> acceptSentinel = true;
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(2);
                }
            }
        }

        if (url == null) {
            System.err.println("--url is required");
            printUsage();
            System.exit(2);
        }

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);

        RunSummary summary = run(ds, batchSize, apply, acceptSentinel);

        System.out.println();
        System.out.println("=== BACKFILL SUMMARY (" + (apply ? "APPLIED" : "DRY RUN") + ") ===");
        System.out.println("run_id      : " + summary.runId);
        summary.changed.forEach((k, v) -> System.out.printf("changed.%-32s %d%n", k, v));
        summary.conflicts.forEach((k, v) -> System.out.printf("conflict.%-31s %d%n", k, v));
        System.out.println("changed_total: " + summary.changedTotal());
        System.out.println("conflict_total: " + summary.conflictTotal());
        if (!apply) {
            System.out.println("(dry run: nothing was written)");
        }
    }

    /** Testable core: build a runner over a datasource and execute once. */
    public static RunSummary run(DataSource dataSource, int batchSize, boolean apply,
                                 boolean acceptSentinelMapping) {
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        ConversationScopeBackfillRunner runner = new ConversationScopeBackfillRunner(
            dataSource, txManager, batchSize, apply, acceptSentinelMapping);
        return runner.run();
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            System.err.println(flag + " requires a value");
            System.exit(2);
        }
        return args[index];
    }

    private static void printUsage() {
        System.err.println("""
            Usage: BackfillCliMain --url <jdbc-url> [options]
              --url <jdbc-url>              target database (required)
              --user <name>                database user (default: postgres)
              --password <pw>              database password
              --batch-size <n>             rows per committed batch (default: 500)
              --apply                      actually write (default is a dry run)
              --accept-sentinel-mapping    reviewed release of the manual-memories
                                           identity ambiguity; without it those buckets
                                           are reported and left untouched
            """);
    }
}