package cn.cctstudio.smartdiypcassistantbackend.hardware;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HardwareSeedData implements ApplicationRunner {

    private final HardwarePartRepository hardwarePartRepository;

    public HardwareSeedData(HardwarePartRepository hardwarePartRepository) {
        this.hardwarePartRepository = hardwarePartRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed(cpu("AMD", "Ryzen 9 9950X3D2", 0, 100, "AM5", 170, "Zen 5 3D V-Cache Dual Edition，旗舰游戏和创作"));
        seed(cpu("AMD", "Ryzen 9 9950X3D", 0, 99, "AM5", 170, "16 核 32 线程，Zen 5 3D V-Cache，旗舰游戏和创作"));
        seed(cpu("AMD", "Ryzen 9 9900X3D", 0, 97, "AM5", 120, "12 核 24 线程，Zen 5 3D V-Cache，高端游戏和生产力"));
        seed(cpu("AMD", "Ryzen 7 9850X3D", 0, 99, "AM5", 120, "Zen 5 3D V-Cache，旗舰游戏处理器"));
        seed(cpu("AMD", "Ryzen 7 9800X3D", 0, 98, "AM5", 120, "8 核 16 线程，Zen 5 3D V-Cache，游戏优先"));
        seed(cpu("AMD", "Ryzen 9 9950X", 0, 96, "AM5", 170, "16 核 32 线程，Zen 5 多线程性能强"));
        seed(cpu("AMD", "Ryzen 9 9900X", 0, 93, "AM5", 120, "12 核 24 线程，Zen 5 高端平台"));
        seed(cpu("AMD", "Ryzen 7 9700X", 0, 88, "AM5", 65, "8 核 16 线程，低功耗 Zen 5"));
        seed(cpu("AMD", "Ryzen 5 9600X", 0, 82, "AM5", 65, "6 核 12 线程，主流 Zen 5 游戏配置"));
        seed(cpu("Intel", "Core Ultra 9 285K", 0, 94, "LGA1851", 125, "8P+16E，Core Ultra 200S 旗舰"));
        seed(cpu("Intel", "Core Ultra 7 270K Plus", 0, 93, "LGA1851", 125, "Core Ultra 200S Plus，高频游戏和创作"));
        seed(cpu("Intel", "Core Ultra 7 265K", 0, 90, "LGA1851", 125, "8P+12E，Core Ultra 200S 高端"));
        seed(cpu("Intel", "Core Ultra 5 250K Plus", 0, 84, "LGA1851", 125, "Core Ultra 200S Plus 主流性能"));
        seed(cpu("Intel", "Core Ultra 5 245K", 0, 80, "LGA1851", 125, "6P+8E，Core Ultra 200S 主流"));
        seed(cpu("AMD", "Ryzen 7 7800X3D", 0, 94, "AM5", 120, "Zen 4 3D V-Cache，仍适合高帧游戏"));
        seed(cpu("AMD", "Ryzen 5 7500F", 0, 76, "AM5", 65, "6 核 12 线程，AM5 入门性价比"));
        seed(cpu("Intel", "Core i7-14700KF", 0, 86, "LGA1700", 125, "14 代高性能游戏和创作处理器"));
        seed(cpu("Intel", "Core i5-14600KF", 0, 80, "LGA1700", 125, "14 代主流游戏处理器"));
        seed(cpu("AMD", "Ryzen 7 5700X3D", 0, 78, "AM4", 105, "AM4 平台游戏升级选择"));
        seed(cpu("AMD", "Ryzen 5 5600", 0, 62, "AM4", 65, "AM4 平台高性价比处理器"));

        seed(motherboard("", "X870E", 0, 0, "AM5", "DDR5", "ATX", "AM5 DDR5 高端扩展主板"));
        seed(motherboard("", "X870", 0, 0, "AM5", "DDR5", "ATX", "AM5 DDR5 高端主板"));
        seed(motherboard("", "B850M", 0, 0, "AM5", "DDR5", "M-ATX", "AM5 DDR5 新一代主流 M-ATX 主板"));
        seed(motherboard("", "B850", 0, 0, "AM5", "DDR5", "ATX", "AM5 DDR5 新一代主流 ATX 主板"));
        seed(motherboard("", "B650E", 0, 0, "AM5", "DDR5", "ATX", "AM5 DDR5 PCIe 5.0 扩展主板"));
        seed(motherboard("", "B650M", 0, 0, "AM5", "DDR5", "M-ATX", "AM5 DDR5 主流 M-ATX 主板"));
        seed(motherboard("", "B650", 0, 0, "AM5", "DDR5", "ATX", "AM5 DDR5 ATX 主板"));
        seed(motherboard("", "Z890", 0, 0, "LGA1851", "DDR5", "ATX", "LGA1851 DDR5 高端超频主板"));
        seed(motherboard("", "B860M", 0, 0, "LGA1851", "DDR5", "M-ATX", "LGA1851 DDR5 主流 M-ATX 主板"));
        seed(motherboard("", "B860", 0, 0, "LGA1851", "DDR5", "ATX", "LGA1851 DDR5 主流 ATX 主板"));
        seed(motherboard("", "H810", 0, 0, "LGA1851", "DDR5", "M-ATX", "LGA1851 入门主板"));
        seed(motherboard("", "Z790", 0, 0, "LGA1700", "DDR5", "ATX", "LGA1700 DDR5 高端主板"));
        seed(motherboard("", "B760M DDR5", 0, 0, "LGA1700", "DDR5", "M-ATX", "LGA1700 DDR5 M-ATX 主板"));
        seed(motherboard("", "B760 DDR4", 0, 0, "LGA1700", "DDR4", "ATX", "LGA1700 DDR4 ATX 主板"));
        seed(motherboard("", "B660M D4", 0, 0, "LGA1700", "DDR4", "M-ATX", "LGA1700 DDR4 M-ATX 主板"));
        seed(motherboard("", "B550M", 0, 0, "AM4", "DDR4", "M-ATX", "AM4 DDR4 M-ATX 主板"));

        seed(gpu("", "RTX 5090", 0, 100, 575, 0, "旗舰 4K/创作显卡，Blackwell 架构"));
        seed(gpu("", "RTX 5080", 0, 96, 360, 0, "高端 4K 游戏和创作显卡"));
        seed(gpu("", "RTX 5070 Ti", 0, 91, 300, 0, "高刷 2K/轻量 4K 显卡"));
        seed(gpu("", "RTX 5070", 0, 86, 250, 0, "主流高端 2K 显卡"));
        seed(gpu("", "RTX 5060 Ti", 0, 78, 180, 0, "主流 1080P/2K 显卡"));
        seed(gpu("", "RTX 5060", 0, 72, 145, 0, "主流 1080P 游戏显卡"));
        seed(gpu("", "RTX 5050", 0, 64, 130, 0, "入门 50 系显卡"));
        seed(gpu("", "RX 9070 XT", 0, 90, 304, 0, "RDNA 4 高端 2K/4K 显卡"));
        seed(gpu("", "RX 9070", 0, 86, 220, 0, "RDNA 4 高刷 2K 显卡"));
        seed(gpu("", "RX 9060 XT", 0, 76, 160, 0, "RDNA 4 主流 1080P/2K 显卡"));
        seed(gpu("", "RTX 4070 SUPER", 0, 84, 220, 0, "上代高刷 2K 显卡"));
        seed(gpu("", "RTX 4070", 0, 80, 200, 0, "上代 2K 高画质显卡"));
        seed(gpu("", "RTX 4060 Ti", 0, 70, 160, 0, "上代主流 1080P/2K 入门显卡"));
        seed(gpu("", "RTX 4060", 0, 64, 115, 0, "上代 1080P 入门显卡"));
        seed(gpu("", "RX 7800 XT", 0, 80, 263, 0, "上代大显存 2K 显卡"));
        seed(gpu("", "RX 7700 XT", 0, 74, 245, 0, "上代 2K 游戏显卡"));

        seed(memory("", "64GB DDR5 6400", 0, 90, "DDR5", "2x32GB 双通道套条，创作和高端游戏"));
        seed(memory("", "32GB DDR5 7200", 0, 88, "DDR5", "2x16GB 双通道高频套条"));
        seed(memory("", "32GB DDR5 6400", 0, 84, "DDR5", "2x16GB 双通道套条"));
        seed(memory("", "32GB DDR5 6000", 0, 82, "DDR5", "2x16GB 双通道套条，AM5 主流甜点"));
        seed(memory("", "16GB DDR5 6000", 0, 70, "DDR5", "2x8GB 双通道入门套条"));
        seed(memory("", "32GB DDR4 3600", 0, 64, "DDR4", "2x16GB 双通道套条"));
        seed(memory("", "16GB DDR4 3200", 0, 56, "DDR4", "2x8GB 双通道套条"));

        seed(storage("", "PCIe 5.0 NVMe SSD 2TB", 0, 94, "新平台高端 PCIe 5.0 固态硬盘"));
        seed(storage("", "PCIe 5.0 NVMe SSD 1TB", 0, 88, "新平台 PCIe 5.0 固态硬盘"));
        seed(storage("", "PCIe 4.0 NVMe SSD 4TB", 0, 88, "大容量 PCIe 4.0 固态硬盘"));
        seed(storage("", "PCIe 4.0 NVMe SSD 2TB", 0, 84, "大容量 PCIe 4.0 固态硬盘"));
        seed(storage("", "PCIe 4.0 NVMe SSD 1TB", 0, 76, "主流 PCIe 4.0 固态硬盘"));

        seed(psu("", "1000W ATX 3.1 Gold PSU", 0, 92, 1000, "ATX 3.1 金牌电源，适合旗舰显卡"));
        seed(psu("", "850W ATX 3.1 Gold PSU", 0, 88, 850, "ATX 3.1 金牌电源，适合高端显卡"));
        seed(psu("", "750W ATX 3.1 Gold PSU", 0, 82, 750, "ATX 3.1 金牌电源，适合主流高端配置"));
        seed(psu("", "650W ATX 3.1 Gold PSU", 0, 76, 650, "ATX 3.1 金牌电源，适合主流配置"));
        seed(psu("", "850W Gold PSU", 0, 80, 850, "850W 金牌电源"));
        seed(psu("", "750W Gold PSU", 0, 76, 750, "750W 金牌电源"));
        seed(psu("", "650W Gold PSU", 0, 70, 650, "650W 金牌电源"));
        seed(psu("", "650W Bronze PSU", 0, 62, 650, "650W 铜牌电源"));

        seed(pcCase("", "E-ATX Airflow Case", 0, 76, "E-ATX", 0, 0, "E-ATX,ATX,M-ATX,ITX", "全塔或大中塔高风道机箱"));
        seed(pcCase("", "ATX Airflow Case", 0, 72, "ATX", 0, 0, "ATX,M-ATX,ITX", "中塔风道机箱"));
        seed(pcCase("", "M-ATX Mesh Case", 0, 68, "M-ATX", 0, 0, "M-ATX,ITX", "紧凑型网孔机箱"));
        seed(pcCase("", "ITX Compact Case", 0, 60, "ITX", 0, 0, "ITX", "迷你 ITX 机箱"));

        seed(cooler("", "360mm AIO Cooler", 0, 88, 0, "360 水冷，适合高端处理器"));
        seed(cooler("", "280mm AIO Cooler", 0, 82, 0, "280 水冷，兼顾静音与性能"));
        seed(cooler("", "240mm AIO Cooler", 0, 76, 0, "240 水冷，主流中高端配置"));
        seed(cooler("", "Dual Tower Cooler", 0, 78, 0, "双塔风冷，适合高性能平台"));
        seed(cooler("", "120mm Tower Cooler", 0, 64, 0, "主流塔式风冷"));
        seed(cooler("", "Low Profile Cooler", 0, 54, 0, "下压式散热，适合小机箱"));
        hardwarePartRepository.deleteByBrandIsNotNullAndBrandNot("");
    }

    private void seed(HardwarePart part) {
        hardwarePartRepository.findByTypeAndModelIgnoreCase(part.getType(), part.getModel())
                .ifPresentOrElse(existing -> existing.update(
                        part.getType(),
                        part.getModel(),
                        part.getPrice(),
                        part.getPerformanceScore(),
                        part.getSocketType(),
                        part.getMemoryType(),
                        part.getFormFactor(),
                        part.getWattage(),
                        part.getGpuLengthMm(),
                        part.getCoolerHeightMm(),
                        part.getSupportedFormFactors(),
                        part.getSpecs(),
                        part.isEnabled()
                ), () -> hardwarePartRepository.save(part));
    }

    private HardwarePart cpu(String brand, String model, int price, int score, String socket, int wattage, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.CPU, model, price, score);
        part.update(HardwarePartType.CPU, model, price, score, socket, "", "", wattage, 0, 0, "", specs, true);
        return part;
    }

    private HardwarePart motherboard(String brand, String model, int price, int score, String socket, String memory, String formFactor, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.MOTHERBOARD, model, price, score);
        part.update(HardwarePartType.MOTHERBOARD, model, price, 0, socket, memory, formFactor, 0, 0, 0, "", specs, true);
        return part;
    }

    private HardwarePart gpu(String brand, String model, int price, int score, int wattage, int length, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.GPU, model, price, score);
        part.update(HardwarePartType.GPU, model, price, score, "", "", "", wattage, 0, 0, "", specs, true);
        return part;
    }

    private HardwarePart memory(String brand, String model, int price, int score, String memory, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.MEMORY, model, price, score);
        part.update(HardwarePartType.MEMORY, model, price, score, "", memory, "", 0, 0, 0, "", specs, true);
        return part;
    }

    private HardwarePart storage(String brand, String model, int price, int score, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.STORAGE, model, price, score);
        part.update(HardwarePartType.STORAGE, model, price, score, "", "", "", 0, 0, 0, "", specs, true);
        return part;
    }

    private HardwarePart psu(String brand, String model, int price, int score, int wattage, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.PSU, model, price, 0);
        part.update(HardwarePartType.PSU, model, price, 0, "", "", "", wattage, 0, 0, "", specs, true);
        return part;
    }

    private HardwarePart pcCase(String brand, String model, int price, int score, String formFactor, int gpuLength, int coolerHeight, String supportedFormFactors, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.CASE, model, price, 0);
        part.update(HardwarePartType.CASE, model, price, 0, "", "", formFactor, 0, 0, 0, supportedFormFactors, specs, true);
        return part;
    }

    private HardwarePart cooler(String brand, String model, int price, int score, int height, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.COOLER, model, price, 0);
        part.update(HardwarePartType.COOLER, model, price, 0, "", "", "", 0, 0, 0, "", specs, true);
        return part;
    }
}
