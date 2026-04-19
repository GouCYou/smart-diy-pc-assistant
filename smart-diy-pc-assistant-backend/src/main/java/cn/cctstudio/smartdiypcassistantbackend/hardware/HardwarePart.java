package cn.cctstudio.smartdiypcassistantbackend.hardware;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "hardware_part")
public class HardwarePart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HardwarePartType type;

    @Column(nullable = false, length = 60)
    private String brand;

    @Column(nullable = false, length = 120)
    private String model;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer performanceScore;

    @Column(length = 40)
    private String socketType;

    @Column(length = 20)
    private String memoryType;

    @Column(length = 40)
    private String formFactor;

    private Integer wattage;

    private Integer gpuLengthMm;

    private Integer coolerHeightMm;

    @Column(length = 120)
    private String supportedFormFactors;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String specs;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected HardwarePart() {
    }

    public HardwarePart(HardwarePartType type, String brand, String model, Integer price, Integer performanceScore) {
        this.type = type;
        this.brand = brand;
        this.model = model;
        this.price = price;
        this.performanceScore = performanceScore;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(
            HardwarePartType type,
            String brand,
            String model,
            Integer price,
            Integer performanceScore,
            String socketType,
            String memoryType,
            String formFactor,
            Integer wattage,
            Integer gpuLengthMm,
            Integer coolerHeightMm,
            String supportedFormFactors,
            String specs,
            boolean enabled
    ) {
        this.type = type;
        this.brand = brand;
        this.model = model;
        this.price = price;
        this.performanceScore = performanceScore;
        this.socketType = blankToNull(socketType);
        this.memoryType = blankToNull(memoryType);
        this.formFactor = blankToNull(formFactor);
        this.wattage = wattage;
        this.gpuLengthMm = gpuLengthMm;
        this.coolerHeightMm = coolerHeightMm;
        this.supportedFormFactors = blankToNull(supportedFormFactors);
        this.specs = blankToNull(specs);
        this.enabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public HardwarePartType getType() {
        return type;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public Integer getPrice() {
        return price;
    }

    public Integer getPerformanceScore() {
        return performanceScore;
    }

    public String getSocketType() {
        return socketType == null ? "" : socketType;
    }

    public String getMemoryType() {
        return memoryType == null ? "" : memoryType;
    }

    public String getFormFactor() {
        return formFactor == null ? "" : formFactor;
    }

    public Integer getWattage() {
        return wattage == null ? 0 : wattage;
    }

    public Integer getGpuLengthMm() {
        return gpuLengthMm == null ? 0 : gpuLengthMm;
    }

    public Integer getCoolerHeightMm() {
        return coolerHeightMm == null ? 0 : coolerHeightMm;
    }

    public String getSupportedFormFactors() {
        return supportedFormFactors == null ? "" : supportedFormFactors;
    }

    public String getSpecs() {
        return specs == null ? "" : specs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
