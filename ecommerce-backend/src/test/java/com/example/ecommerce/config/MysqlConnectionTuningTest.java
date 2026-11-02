package com.example.ecommerce.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Production uses MySQL, which the H2-based tests never exercise; this checks the pool settings directly. */
class MysqlConnectionTuningTest {

    @Test
    void mysqlPoolGetsValidSettingsWhenThePoolSizeIsStillUnset() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://db.example:3306/shop?sslMode=REQUIRED");
        assertDoesNotThrow(() -> new MysqlConnectionTuning().postProcessBeforeInitialization(ds, "dataSource"));
        assertEquals(4, ds.getMinimumIdle());
        assertEquals("true", ds.getDataSourceProperties().getProperty("useLocalSessionState"));
        assertDoesNotThrow(ds::validate, "Hikari accepts the resulting configuration");
    }

    @Test
    void respectsASmallerConfiguredPool() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://db.example:3306/shop");
        ds.setMaximumPoolSize(3);
        new MysqlConnectionTuning().postProcessBeforeInitialization(ds, "dataSource");
        assertEquals(3, ds.getMinimumIdle());
    }

    @Test
    void leavesOtherDatabasesAlone() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:test");
        new MysqlConnectionTuning().postProcessBeforeInitialization(ds, "dataSource");
        assertTrue(ds.getDataSourceProperties().isEmpty());
    }
}
