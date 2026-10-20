package com.cfs.Drive_Service.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/** Adds explicit ownership to version rows and backfills existing H2 history. */
@Component
public class DriveFileVersionOwnerMigration implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DriveFileVersionOwnerMigration(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!"H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return;
        }
        jdbcTemplate.execute("ALTER TABLE DRIVE_FILE_VERSIONS ADD COLUMN IF NOT EXISTS OWNER_ID UUID");
        jdbcTemplate.update("UPDATE DRIVE_FILE_VERSIONS V SET OWNER_ID = (SELECT I.OWNER_ID FROM DRIVE_ITEM I WHERE I.ID = V.ITEM_ID) WHERE OWNER_ID IS NULL");
    }
}
