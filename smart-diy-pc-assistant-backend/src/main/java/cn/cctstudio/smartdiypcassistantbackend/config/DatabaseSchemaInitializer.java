package cn.cctstudio.smartdiypcassistantbackend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_runtime_setting (
                    id bigint NOT NULL,
                    api_key varchar(256) NULL,
                    auto_build_enabled bit(1) NOT NULL,
                    base_url varchar(500) NULL,
                    build_trigger_mode enum('CONSERVATIVE','BALANCED','SENSITIVE') NULL,
                    color_match_mode enum('STRICT','PREFER','FLEXIBLE') NULL,
                    context_message_limit int NULL,
                    extra_system_prompt text NULL,
                    model_name varchar(120) NULL,
                    reply_max_chars int NULL,
                    reply_min_chars int NULL,
                    require_purchasable_products bit(1) NOT NULL,
                    timeout_seconds int NULL,
                    updated_at datetime(6) NOT NULL,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        jdbcTemplate.execute("ALTER TABLE ai_chat_message MODIFY content LONGTEXT NOT NULL");
        jdbcTemplate.execute("""
                ALTER TABLE user_notification MODIFY type ENUM(
                    'SYSTEM',
                    'FOLLOW',
                    'COMMUNITY_POST_REPLY',
                    'COMMUNITY_COMMENT_REPLY',
                    'COMMUNITY_POST_MENTION',
                    'COMMUNITY_COMMENT_MENTION',
                    'NEWS_COMMENT_MENTION',
                    'NEWS_COMMENT_REPLY'
                ) NOT NULL
                """);
    }
}
