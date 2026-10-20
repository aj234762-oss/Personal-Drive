package com.cfs.Drive_Service.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Older H2 schemas stored enums as native ENUM columns. Such a column rejects
 * newly introduced status values even after Hibernate updates the entity model.
 * Store status as VARCHAR so adding statuses does not require a table rebuild.
 */
@Component
public class DriveItemStatusSchemaMigration implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DriveItemStatusSchemaMigration(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!"H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return;
        }

        String statusType = jdbcTemplate.query(
                "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'DRIVE_ITEM' AND COLUMN_NAME = 'STATUS'",
                result -> result.next() ? result.getString(1) : null);

        if ("ENUM".equalsIgnoreCase(statusType)) {
            jdbcTemplate.execute("ALTER TABLE DRIVE_ITEM ALTER COLUMN STATUS SET DATA TYPE VARCHAR(16)");
        }
    }
}
