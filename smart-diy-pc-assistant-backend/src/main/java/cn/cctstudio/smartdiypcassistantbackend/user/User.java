package cn.cctstudio.smartdiypcassistantbackend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(unique = true, length = 128)
    private String email;

    @Column(unique = true, length = 64)
    private String nickname;

    @Column(length = 512)
    private String avatarUrl;

    @Column(length = 512)
    private String backgroundImage;

    @Column(length = 16)
    private String gender;

    @Column(length = 20)
    private String birthday;

    @Column(length = 80)
    private String countryOrRegion;

    @Column(length = 80)
    private String province;

    @Column(length = 80)
    private String city;

    @Column(nullable = false)
    private boolean profilePublic;

    @Column
    private Boolean spacePublic;

    @Column(length = 300)
    private String bio;

    @Column(nullable = false, length = 120)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled;

    @Column
    private LocalDateTime banExpiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    protected User() {
    }

    public User(String username, String email, String passwordHash, UserRole role) {
        this.username = username;
        this.email = email;
        this.nickname = username;
        this.bio = "";
        this.profilePublic = true;
        this.spacePublic = true;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname == null || nickname.isBlank() ? username : nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getAvatarKey() {
        return avatarUrl;
    }

    public String getBackgroundImage() {
        return backgroundImage;
    }

    public String getBackgroundImageKey() {
        return backgroundImage;
    }

    public String getGender() {
        return gender == null ? "" : gender;
    }

    public String getBirthday() {
        return birthday == null ? "" : birthday;
    }

    public String getCountryOrRegion() {
        return countryOrRegion == null ? "" : countryOrRegion;
    }

    public String getProvince() {
        return province == null ? "" : province;
    }

    public String getCity() {
        return city == null ? "" : city;
    }

    public boolean isProfilePublic() {
        return profilePublic;
    }

    public boolean isSpacePublic() {
        return spacePublic == null || spacePublic;
    }

    public String getBio() {
        return bio == null ? "" : bio;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getBanExpiresAt() {
        return banExpiresAt;
    }

    public boolean isCurrentlyBanned() {
        return !enabled && (banExpiresAt == null || banExpiresAt.isAfter(LocalDateTime.now()));
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateProfile(String nickname, String avatarUrl, String bio) {
        this.nickname = nickname == null || nickname.isBlank() ? this.username : nickname.trim();
        this.avatarUrl = avatarUrl == null || avatarUrl.isBlank() ? null : avatarUrl.trim();
        this.bio = bio == null ? "" : bio.trim();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateProfile(
            String nickname,
            String avatarUrl,
            String backgroundImage,
            String bio,
            String gender,
            String birthday,
            String countryOrRegion,
            String province,
            String city,
            Boolean profilePublic,
            Boolean spacePublic
    ) {
        this.nickname = nickname == null || nickname.isBlank() ? this.username : nickname.trim();
        this.avatarUrl = avatarUrl == null || avatarUrl.isBlank() ? null : avatarUrl.trim();
        this.backgroundImage = backgroundImage == null || backgroundImage.isBlank() ? null : backgroundImage.trim();
        this.bio = bio == null ? "" : bio.trim();
        this.gender = gender == null || gender.isBlank() ? "" : gender.trim();
        this.birthday = birthday == null || birthday.isBlank() ? "" : birthday.trim();
        this.countryOrRegion = countryOrRegion == null || countryOrRegion.isBlank() ? "" : countryOrRegion.trim();
        this.province = province == null || province.isBlank() ? "" : province.trim();
        this.city = city == null || city.isBlank() ? "" : city.trim();
        if (profilePublic != null) {
            this.profilePublic = profilePublic;
        }
        if (spacePublic != null) {
            this.spacePublic = spacePublic;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void changeRole(UserRole role) {
        this.role = role;
        this.updatedAt = LocalDateTime.now();
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            this.banExpiresAt = null;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void banUntil(LocalDateTime banExpiresAt) {
        this.enabled = false;
        this.banExpiresAt = banExpiresAt;
        this.updatedAt = LocalDateTime.now();
    }

    public void clearExpiredBan() {
        if (!enabled && banExpiresAt != null && !banExpiresAt.isAfter(LocalDateTime.now())) {
            this.enabled = true;
            this.banExpiresAt = null;
            this.updatedAt = LocalDateTime.now();
        }
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = LocalDateTime.now();
    }
}
