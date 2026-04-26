package cn.cctstudio.smartdiypcassistantbackend.auth;

import cn.cctstudio.smartdiypcassistantbackend.config.properties.VerificationCodeProperties;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import cn.cctstudio.smartdiypcassistantbackend.user.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;

@Service
public class EmailVerificationService {

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String CODE_CHARS = LETTERS + DIGITS;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_KEY_PREFIX = "auth:email-code:";
    private static final String COOLDOWN_KEY_PREFIX = "auth:email-code:cooldown:";
    private static final String PURPOSE_REGISTER = "register";
    private static final String PURPOSE_RESET_PASSWORD = "reset-password";

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final VerificationCodeProperties properties;
    private final String mailUsername;

    public EmailVerificationService(
            StringRedisTemplate redisTemplate,
            JavaMailSender mailSender,
            UserRepository userRepository,
            VerificationCodeProperties properties,
            @Value("${spring.mail.username:}") String mailUsername
    ) {
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.properties = properties;
        this.mailUsername = mailUsername;
    }

    public void sendRegisterCode(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException("该邮箱已被绑定");
        }
        if (!StringUtils.hasText(mailUsername)) {
            throw new BusinessException("邮箱服务未配置");
        }

        String cooldownKey = cooldownKey(PURPOSE_REGISTER, email);
        Boolean cooldownSet = redisTemplate.opsForValue().setIfAbsent(
                cooldownKey,
                "1",
                Duration.ofSeconds(properties.resendSeconds())
        );
        if (!Boolean.TRUE.equals(cooldownSet)) {
            Long ttl = redisTemplate.getExpire(cooldownKey);
            long seconds = ttl == null || ttl < 1 ? properties.resendSeconds() : ttl;
            throw new BusinessException("验证码发送过于频繁，请 " + seconds + " 秒后再试");
        }

        String code = createCode();
        String codeKey = codeKey(PURPOSE_REGISTER, email);
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofMinutes(properties.expireMinutes()));
        try {
            sendMail(email, code);
        } catch (MessagingException | MailException | UnsupportedEncodingException exception) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(cooldownKey);
            throw new BusinessException("验证码邮件发送失败，请稍后重试");
        }
    }

    public void sendResetPasswordCode(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (userRepository.findByEmail(email).isEmpty()) {
            throw new BusinessException("该邮箱未注册");
        }
        if (!StringUtils.hasText(mailUsername)) {
            throw new BusinessException("邮箱服务未配置");
        }

        String cooldownKey = cooldownKey(PURPOSE_RESET_PASSWORD, email);
        Boolean cooldownSet = redisTemplate.opsForValue().setIfAbsent(
                cooldownKey,
                "1",
                Duration.ofSeconds(properties.resendSeconds())
        );
        if (!Boolean.TRUE.equals(cooldownSet)) {
            Long ttl = redisTemplate.getExpire(cooldownKey);
            long seconds = ttl == null || ttl < 1 ? properties.resendSeconds() : ttl;
            throw new BusinessException("验证码发送过于频繁，请 " + seconds + " 秒后再试");
        }

        String code = createCode();
        String codeKey = codeKey(PURPOSE_RESET_PASSWORD, email);
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofMinutes(properties.expireMinutes()));
        try {
            sendMail(email, code);
        } catch (MessagingException | MailException | UnsupportedEncodingException exception) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(cooldownKey);
            throw new BusinessException("验证码邮件发送失败，请稍后重试");
        }
    }

    public void verifyRegisterCodeAndConsume(String rawEmail, String rawCode) {
        verifyAndConsume(PURPOSE_REGISTER, rawEmail, rawCode);
    }

    public void verifyResetPasswordCode(String rawEmail, String rawCode) {
        verify(PURPOSE_RESET_PASSWORD, rawEmail, rawCode);
    }

    public void consumeResetPasswordCode(String rawEmail) {
        redisTemplate.delete(codeKey(PURPOSE_RESET_PASSWORD, normalizeEmail(rawEmail)));
    }

    private void verifyAndConsume(String purpose, String rawEmail, String rawCode) {
        verify(purpose, rawEmail, rawCode);
        redisTemplate.delete(codeKey(purpose, normalizeEmail(rawEmail)));
    }

    private void verify(String purpose, String rawEmail, String rawCode) {
        String email = normalizeEmail(rawEmail);
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        String codeKey = codeKey(purpose, email);
        String storedCode = redisTemplate.opsForValue().get(codeKey);
        if (!StringUtils.hasText(storedCode)) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!storedCode.equalsIgnoreCase(code)) {
            throw new BusinessException("验证码不正确");
        }
    }

    private void sendMail(String email, String code) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(mailUsername, "Smart DIY 装机助手");
        helper.setTo(email);
        helper.setSubject("账户安全验证");
        helper.setText(createMailHtml(code), true);
        helper.addInline("appLogo", new ClassPathResource("mail/logo.png"), "image/png");
        mailSender.send(message);
    }

    private String createCode() {
        char[] chars = new char[6];
        chars[0] = LETTERS.charAt(RANDOM.nextInt(LETTERS.length()));
        chars[1] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
        for (int i = 2; i < chars.length; i += 1) {
            chars[i] = CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length()));
        }
        for (int i = chars.length - 1; i > 0; i -= 1) {
            int j = RANDOM.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }

    private String createMailHtml(String code) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <body style="margin:0;padding:0;background:#f5f7fb;font-family:-apple-system,BlinkMacSystemFont,'SF Pro Text','PingFang SC','Segoe UI',sans-serif;color:#111827;">
                  <div style="max-width:560px;margin:0 auto;padding:36px 18px;">
                    <div style="background:rgba(255,255,255,0.92);border:1px solid #e8edf5;border-radius:30px;box-shadow:0 24px 70px rgba(24,39,75,0.10);overflow:hidden;">
                      <div style="padding:32px 32px 18px;">
                        <div style="width:54px;height:54px;border-radius:18px;overflow:hidden;">
                          <img src="cid:appLogo" width="54" height="54" alt="Smart DIY 装机助手" style="width:54px;height:54px;border-radius:18px;border:0;display:block;">
                        </div>
                        <h1 style="margin:24px 0 8px;font-size:26px;line-height:1.25;font-weight:800;color:#111827;">账户安全验证</h1>
                        <p style="margin:0;color:#667085;font-size:15px;line-height:1.7;">欢迎使用 Smart DIY 装机助手。请输入下方验证码完成账户安全验证，验证码 %d 分钟内有效。</p>
                      </div>
                      <div style="margin:10px 32px 28px;padding:22px 16px;border-radius:24px;background:linear-gradient(180deg,#f8fbff,#eef5ff);border:1px solid #dce8fb;text-align:center;">
                        <div style="font-size:34px;line-height:1;font-weight:900;letter-spacing:10px;color:#111827;">%s</div>
                      </div>
                      <div style="padding:0 32px 34px;">
                        <p style="margin:0;color:#98a2b3;font-size:13px;line-height:1.7;">如果不是你本人操作，可以忽略这封邮件。为了账号安全，请不要把验证码转发给任何人。</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(properties.expireMinutes(), code);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String codeKey(String purpose, String email) {
        return CODE_KEY_PREFIX + purpose + ":" + email;
    }

    private String cooldownKey(String purpose, String email) {
        return COOLDOWN_KEY_PREFIX + purpose + ":" + email;
    }
}
