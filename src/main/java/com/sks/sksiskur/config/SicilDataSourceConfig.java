package com.sks.sksiskur.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "sicil.enabled", havingValue = "true", matchIfMissing = true)
public class SicilDataSourceConfig {

    @Bean
    @Primary
    DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password
    ) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setPoolName("sksiskur");
        ds.setConnectionInitSql("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");
        return ds;
    }

    @Bean(name = "isicilDataSource")
    DataSource isicilDataSource(
            @Value("${sicil.isicil.url}") String url,
            @Value("${sicil.isicil.username}") String username,
            @Value("${sicil.isicil.password}") String password
    ) {
        return hikari(url, username, password);
    }

    @Bean(name = "asicilDataSource")
    DataSource asicilDataSource(
            @Value("${sicil.asicil.url}") String url,
            @Value("${sicil.asicil.username}") String username,
            @Value("${sicil.asicil.password}") String password
    ) {
        return hikari(url, username, password);
    }

    @Bean(name = "isicilJdbcTemplate")
    JdbcTemplate isicilJdbcTemplate(@Qualifier("isicilDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "asicilJdbcTemplate")
    JdbcTemplate asicilJdbcTemplate(@Qualifier("asicilDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private HikariDataSource hikari(String url, String username, String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setMaximumPoolSize(3);
        ds.setMinimumIdle(0);
        ds.setConnectionTimeout(8000);
        ds.setReadOnly(true);
        ds.setPoolName("sicil-" + url.hashCode());
        return ds;
    }
}
