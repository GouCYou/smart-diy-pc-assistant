package cn.cctstudio.smartdiypcassistantbackend.auth;

import cn.cctstudio.smartdiypcassistantbackend.auth.dto.CaptchaResponse;
import cn.cctstudio.smartdiypcassistantbackend.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class HumanCaptchaService {

    private static final String CAPTCHA_KEY_PREFIX = "auth:human-captcha:";
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    public HumanCaptchaService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public CaptchaResponse createCaptcha() {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String code = createCode();
        redisTemplate.opsForValue().set(captchaKey(captchaId), code, Duration.ofMinutes(5));
        return new CaptchaResponse(captchaId, "data:image/png;base64," + encodePng(createImage(code)));
    }

    public void verifyAndConsume(String captchaId, String rawCode) {
        verify(captchaId, rawCode, true);
    }

    public void verify(String captchaId, String rawCode) {
        verify(captchaId, rawCode, false);
    }

    private void verify(String captchaId, String rawCode, boolean consume) {
        String cleanId = captchaId == null ? "" : captchaId.trim();
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(cleanId) || !StringUtils.hasText(code)) {
            throw new BusinessException("请先输入验证码");
        }
        String key = captchaKey(cleanId);
        String storedCode = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(storedCode)) {
            throw new BusinessException("验证码已过期，请刷新后重试");
        }
        if (!storedCode.equalsIgnoreCase(code)) {
            throw new BusinessException("验证码不正确");
        }
        if (consume) {
            redisTemplate.delete(key);
        }
    }

    private String createCode() {
        StringBuilder builder = new StringBuilder(4);
        for (int i = 0; i < 4; i += 1) {
            builder.append(CAPTCHA_CHARS.charAt(RANDOM.nextInt(CAPTCHA_CHARS.length())));
        }
        return builder.toString();
    }

    private BufferedImage createImage(String code) {
        int width = 140;
        int height = 52;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(238, 245, 255));
            graphics.fillRoundRect(0, 0, width, height, 18, 18);

            graphics.setStroke(new BasicStroke(2.4f));
            graphics.setColor(new Color(155, 188, 255, 190));
            graphics.drawArc(8, 4, 126, 50, 10, 150);
            graphics.setColor(new Color(209, 221, 243, 220));
            graphics.drawArc(4, 12, 132, 42, 195, 150);

            graphics.setFont(new Font("Arial", Font.BOLD, 28));
            for (int i = 0; i < code.length(); i += 1) {
                int x = 22 + i * 28;
                int y = 37 + RANDOM.nextInt(8);
                double rotate = Math.toRadians(RANDOM.nextInt(21) - 10);
                graphics.rotate(rotate, x, y);
                graphics.setColor(new Color(17, 24, 39));
                graphics.drawString(String.valueOf(code.charAt(i)), x, y);
                graphics.rotate(-rotate, x, y);
            }

            for (int i = 0; i < 10; i += 1) {
                graphics.setColor(new Color(55, 125, 255, 55 + RANDOM.nextInt(70)));
                int size = 2 + RANDOM.nextInt(3);
                graphics.fillOval(RANDOM.nextInt(width), RANDOM.nextInt(height), size, size);
            }
            return image;
        } finally {
            graphics.dispose();
        }
    }

    private String encodePng(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new BusinessException("验证码生成失败");
        }
    }

    private String captchaKey(String captchaId) {
        return CAPTCHA_KEY_PREFIX + captchaId;
    }
}
