package com.twithu.orderliabilityagent.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false) // 设置false 不生产代理类 每次注入配置类都是一个新的实例，更轻量级的选择
public class DataSourceConfig {

    /* ---------------------- MySQL ----------------------- */
    @Bean
    @Primary //存在多个时，设置默认源
    @ConfigurationProperties("spring.datasource.mysql")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    // @ConfigurationProperties("app.datasource.mysql.configuration")
    public HikariDataSource mysqlDataSource(@Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public JdbcTemplate mysqlJdbcTemplate(@Qualifier("mysqlDataSource") HikariDataSource mysqlDataSource) {
        return new JdbcTemplate(mysqlDataSource);
    }

    /* ---------------------- PostgreSQL ----------------------- */

    @Bean
    @ConfigurationProperties("spring.datasource.pgsql")
    public DataSourceProperties pgDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    // @ConfigurationProperties("spring.datasource.pgsql.configuration")
    public HikariDataSource pgDataSource(@Qualifier("pgDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    public JdbcTemplate pgJdbcTemplate(@Qualifier("pgDataSource") HikariDataSource pgDataSource) {
        return new JdbcTemplate(pgDataSource);
    }
}
