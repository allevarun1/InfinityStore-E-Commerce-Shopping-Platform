package com.example.ecommerce.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * The database is in another city from the server, so every round trip costs tens of milliseconds. By default the
 * MySQL driver re-sends "SET autocommit" and "SET SESSION TRANSACTION READ ONLY/WRITE" on every transaction even when
 * nothing changed; these settings make it remember the session state and skip them, and cache prepared statements.
 * Applied only to MySQL connections (tests run on H2, which rejects these driver options).
 */
@Component
public class MysqlConnectionTuning implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof HikariDataSource ds && ds.getJdbcUrl() != null && ds.getJdbcUrl().startsWith("jdbc:mysql")) {
            ds.addDataSourceProperty("useLocalSessionState", "true");
            ds.addDataSourceProperty("useLocalTransactionState", "true");
            ds.addDataSourceProperty("cacheServerConfiguration", "true");
            ds.addDataSourceProperty("cachePrepStmts", "true");
            ds.addDataSourceProperty("prepStmtCacheSize", "250");
            ds.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            ds.addDataSourceProperty("rewriteBatchedStatements", "true");
            ds.addDataSourceProperty("maintainTimeStats", "false");
            // Keep warm connections ready so no request pays for a new TLS connection to the database.
            // The pool size may still be unset (-1) here; Hikari then defaults it to 10.
            int max = ds.getMaximumPoolSize();
            ds.setMinimumIdle(max > 0 ? Math.min(4, max) : 4);
            ds.setKeepaliveTime(120_000);
        }
        return bean;
    }
}
