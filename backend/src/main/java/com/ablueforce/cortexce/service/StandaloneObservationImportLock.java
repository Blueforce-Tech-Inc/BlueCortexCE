package com.ablueforce.cortexce.service;

import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.TreeSet;

/** Coordinates idempotent standalone Observation imports across application instances. */
@Service
public class StandaloneObservationImportLock {

    private static final String LOCK_SQL =
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))";

    private final NamedParameterJdbcTemplate jdbc;

    public StandaloneObservationImportLock(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The caller's import transaction holds these locks through duplicate lookup and insert. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Collection<String> lockKeys) {
        for (String lockKey : new TreeSet<>(lockKeys)) {
            jdbc.getJdbcTemplate().query(LOCK_SQL,
                (PreparedStatementSetter) statement -> statement.setString(1, lockKey),
                (RowCallbackHandler) resultSet -> { });
        }
    }
}
