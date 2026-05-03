package cn.cctstudio.smartdiypcassistantbackend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DatabaseSchemaInitializer implements ApplicationRunner {

    private static final String SERIAL_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

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
        executeIgnoring("ALTER TABLE ai_chat_message MODIFY content LONGTEXT NOT NULL");
        ensureAiBuildTaskSchema();
        executeIgnoring("""
                ALTER TABLE user_notification MODIFY type ENUM(
                    'SYSTEM',
                    'FOLLOW',
                    'COMMUNITY_POST_REPLY',
                    'COMMUNITY_COMMENT_REPLY',
                    'COMMUNITY_POST_MENTION',
                    'COMMUNITY_COMMENT_MENTION',
                    'NEWS_COMMENT_MENTION',
                    'NEWS_COMMENT_REPLY',
                    'FEEDBACK_RESOLVED'
                ) NOT NULL
                """);
        ensureUserMemorySchema();
        ensureBuildConfigSchema();
        ensureBuildConfigAiReviewSchema();
        ensureFeedbackSchema();
        cleanLegacyUserData();
    }

    private void ensureAiBuildTaskSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_build_task (
                    id bigint NOT NULL AUTO_INCREMENT,
                    user_id bigint NOT NULL,
                    conversation_id bigint NOT NULL,
                    attached_build_id bigint NULL,
                    request_message LONGTEXT NOT NULL,
                    status enum('QUEUED','RUNNING','SUCCEEDED','FAILED') NOT NULL,
                    progress int NOT NULL,
                    stage_title varchar(40) NOT NULL,
                    stage_description varchar(160) NOT NULL,
                    user_facing_message varchar(240) NOT NULL,
                    build_config_id bigint NULL,
                    build_config_title varchar(80) NULL,
                    build_config_serial_number varchar(20) NULL,
                    error_message text NULL,
                    created_at datetime(6) NOT NULL,
                    updated_at datetime(6) NOT NULL,
                    finished_at datetime(6) NULL,
                    PRIMARY KEY (id),
                    KEY idx_ai_build_task_user_conversation (user_id, conversation_id, status, updated_at),
                    KEY idx_ai_build_task_user_status (user_id, status, updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
    }

    private void ensureUserMemorySchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_memory (
                    id bigint NOT NULL AUTO_INCREMENT,
                    user_id bigint NOT NULL,
                    budget_preference varchar(120) NULL,
                    usage_preference varchar(240) NULL,
                    cpu_platform_preference varchar(160) NULL,
                    gpu_platform_preference varchar(160) NULL,
                    preferred_brands varchar(240) NULL,
                    rejected_brands varchar(240) NULL,
                    appearance_preference varchar(180) NULL,
                    noise_preference varchar(160) NULL,
                    upgrade_plan varchar(240) NULL,
                    created_at datetime(6) NOT NULL,
                    updated_at datetime(6) NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_user_memory_user_id (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
    }

    private void ensureBuildConfigSchema() {
        executeIgnoring("ALTER TABLE pc_build_config ADD COLUMN serial_number varchar(9) DEFAULT NULL AFTER user_id");
        executeIgnoring("ALTER TABLE pc_build_config ADD UNIQUE KEY uk_build_config_serial_number (serial_number)");
        executeIgnoring("ALTER TABLE pc_build_config ADD COLUMN ai_review LONGTEXT NULL AFTER part_selections");
        executeIgnoring("ALTER TABLE pc_build_config ADD COLUMN ai_review_updated_at datetime(6) NULL AFTER ai_review");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS pc_build_config_favorite (
                    id bigint NOT NULL AUTO_INCREMENT,
                    user_id bigint NOT NULL,
                    build_config_id bigint NOT NULL,
                    created_at datetime(6) NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_build_config_favorite_user_build (user_id, build_config_id),
                    KEY idx_build_config_favorite_user (user_id),
                    KEY idx_build_config_favorite_build (build_config_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        executeIgnoring("""
                INSERT IGNORE INTO pc_build_config_favorite (user_id, build_config_id, created_at)
                SELECT user_id, id, COALESCE(updated_at, NOW(6))
                FROM pc_build_config
                WHERE favorite = b'1'
                """);
        backfillBuildConfigSerialNumbers();
    }

    private void ensureBuildConfigAiReviewSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS pc_build_config_ai_review (
                    id bigint NOT NULL AUTO_INCREMENT,
                    build_config_id bigint NOT NULL,
                    user_id bigint NOT NULL,
                    review_content LONGTEXT NOT NULL,
                    created_at datetime(6) NOT NULL,
                    updated_at datetime(6) NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_build_ai_review_build_user (build_config_id, user_id),
                    KEY idx_build_ai_review_user (user_id, updated_at),
                    KEY idx_build_ai_review_build (build_config_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        executeIgnoring("""
                INSERT IGNORE INTO pc_build_config_ai_review
                    (build_config_id, user_id, review_content, created_at, updated_at)
                SELECT id, user_id, ai_review,
                       COALESCE(ai_review_updated_at, updated_at, NOW(6)),
                       COALESCE(ai_review_updated_at, updated_at, NOW(6))
                FROM pc_build_config
                WHERE ai_review IS NOT NULL AND TRIM(ai_review) <> ''
                """);
    }

    private void ensureFeedbackSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_feedback (
                    id bigint NOT NULL AUTO_INCREMENT,
                    user_id bigint NOT NULL,
                    content TEXT NOT NULL,
                    image_keys TEXT NOT NULL,
                    status enum('SUBMITTED','RECEIVED','RESOLVED') NOT NULL,
                    created_at datetime(6) NOT NULL,
                    updated_at datetime(6) NOT NULL,
                    received_at datetime(6) NULL,
                    resolved_at datetime(6) NULL,
                    PRIMARY KEY (id),
                    KEY idx_user_feedback_user_created (user_id, created_at),
                    KEY idx_user_feedback_status (status, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
    }

    private void backfillBuildConfigSerialNumbers() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM pc_build_config WHERE serial_number IS NULL OR serial_number = ''",
                Long.class
        );
        for (Long id : ids) {
            String serial = generateUniqueSerialNumber();
            jdbcTemplate.update("UPDATE pc_build_config SET serial_number = ? WHERE id = ?", serial, id);
        }
    }

    private String generateUniqueSerialNumber() {
        for (int attempt = 0; attempt < 50; attempt++) {
            String serial = "#" + randomSerialBody();
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pc_build_config WHERE serial_number = ?",
                    Integer.class,
                    serial
            );
            if (count == null || count == 0) {
                return serial;
            }
        }
        String fallback = Long.toString(Math.abs(RANDOM.nextLong()), 36).toUpperCase(Locale.ROOT);
        return "#" + (fallback + "00000000").substring(0, 8);
    }

    private String randomSerialBody() {
        StringBuilder builder = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            builder.append(SERIAL_ALPHABET.charAt(RANDOM.nextInt(SERIAL_ALPHABET.length())));
        }
        return builder.toString();
    }

    private void cleanLegacyUserData() {
        executeIgnoring("""
                UPDATE sys_user
                SET avatar_url = NULL
                WHERE avatar_url IS NOT NULL
                  AND (
                      LOWER(avatar_url) LIKE '%.gif'
                      OR LOWER(avatar_url) LIKE '%.gif?%'
                      OR LOWER(avatar_url) LIKE '%.gif#%'
                  )
                """);
        List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT id, username FROM sys_user");
        for (Map<String, Object> row : users) {
            Long id = ((Number) row.get("id")).longValue();
            String username = row.get("username") == null ? "" : row.get("username").toString();
            if (isAsciiUsername(username)) {
                continue;
            }
            String base = toAsciiUsernameBase(username);
            jdbcTemplate.update("UPDATE sys_user SET username = ? WHERE id = ?", uniqueUsername(base, id), id);
        }
    }

    private boolean isAsciiUsername(String username) {
        return username != null && username.matches("^[A-Za-z][A-Za-z0-9_]{2,63}$");
    }

    private String toAsciiUsernameBase(String username) {
        String text = username == null ? "" : username.trim();
        if (text.equals("鬼画符")) {
            return "guihuafu";
        }
        if (text.equals("梁崇熙") || text.equals("两充系")) {
            return "liangchongxi";
        }
        String cleaned = text.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.matches("^[A-Za-z][A-Za-z0-9_]{2,63}$")) {
            return cleaned;
        }
        if (cleaned.matches("^[0-9_].*")) {
            return "user" + cleaned;
        }
        return "user";
    }

    private String uniqueUsername(String base, Long userId) {
        String cleanBase = base == null || base.isBlank() ? "user" : base.toLowerCase(Locale.ROOT);
        cleanBase = cleanBase.replaceAll("[^a-z0-9_]", "");
        if (!cleanBase.matches("^[a-z].*")) {
            cleanBase = "user" + cleanBase;
        }
        if (cleanBase.length() > 50) {
            cleanBase = cleanBase.substring(0, 50);
        }
        String suffix = String.valueOf(userId == null ? RANDOM.nextInt(90) + 10 : userId);
        for (int attempt = 0; attempt < 50; attempt++) {
            String candidate = cleanBase + suffix + (attempt == 0 ? "" : RANDOM.nextInt(90) + 10);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_user WHERE username = ? AND id <> ?",
                    Integer.class,
                    candidate,
                    userId
            );
            if (count == null || count == 0) {
                return candidate;
            }
        }
        return "user" + Math.abs(RANDOM.nextInt(900000) + 100000);
    }

    private void executeIgnoring(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (DataAccessException ignored) {
        }
    }
}
