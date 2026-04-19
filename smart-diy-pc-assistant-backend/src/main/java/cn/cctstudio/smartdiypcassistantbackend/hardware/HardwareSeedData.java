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
        normalizeOldGpuBrands();
        seed(cpu("AMD", "Ryzen 5 7500F", 1099, 82, "AM5", 65, "6 核 12 线程，适合主流游戏配置"));
        seed(cpu("Intel", "Core i5-12400F", 799, 72, "LGA1700", 65, "6 核 12 线程，入门到主流游戏"));
        seed(cpu("AMD", "Ryzen 7 7800X3D", 2499, 96, "AM5", 120, "游戏性能强，适合高刷新率配置"));
        seed(cpu("Intel", "Core i5-13490F", 1199, 80, "LGA1700", 65, "10 核 16 线程，主流游戏和生产力"));
        seed(cpu("Intel", "Core i7-14700KF", 2699, 94, "LGA1700", 125, "高性能游戏和创作处理器"));
        seed(cpu("AMD", "Ryzen 5 5600", 699, 68, "AM4", 65, "AM4 平台高性价比处理器"));
        seed(cpu("AMD", "Ryzen 7 5700X3D", 1399, 86, "AM4", 105, "AM4 平台游戏升级选择"));

        seed(motherboard("微星", "MAG B650M MORTAR WIFI", 1299, 78, "AM5", "DDR5", "M-ATX", "AM5 平台，带 Wi-Fi"));
        seed(motherboard("华硕", "PRIME B660M-K D4", 699, 62, "LGA1700", "DDR4", "M-ATX", "LGA1700 平台，DDR4 内存"));
        seed(motherboard("技嘉", "B650 AORUS ELITE AX", 1499, 82, "AM5", "DDR5", "ATX", "ATX 版型，扩展能力更好"));
        seed(motherboard("华硕", "TUF GAMING B650M-PLUS WIFI", 1299, 79, "AM5", "DDR5", "M-ATX", "AM5 主流游戏主板"));
        seed(motherboard("微星", "B760M MORTAR WIFI II DDR5", 1199, 76, "LGA1700", "DDR5", "M-ATX", "LGA1700 DDR5 主板"));
        seed(motherboard("华擎", "B550M Steel Legend", 599, 62, "AM4", "DDR4", "M-ATX", "AM4 DDR4 主板"));
        seed(motherboard("技嘉", "B760 AORUS ELITE AX DDR4", 1099, 73, "LGA1700", "DDR4", "ATX", "LGA1700 DDR4 ATX 主板"));

        seed(gpu("七彩虹", "iGame GeForce RTX 4060 Ti Ultra W 8G", 2799, 78, 160, 245, "2K 入门游戏显卡"));
        seed(gpu("华硕", "DUAL GeForce RTX 4070 SUPER O12G", 4599, 92, 220, 267, "2K 高刷和轻量 4K"));
        seed(gpu("蓝宝石", "PURE Radeon RX 7800 XT 16G", 3899, 88, 263, 280, "大显存，适合 2K 高画质"));
        seed(gpu("微星", "GeForce RTX 4060 VENTUS 2X 8G", 2199, 70, 115, 199, "1080P/2K 入门显卡"));
        seed(gpu("技嘉", "GeForce RTX 4070 WINDFORCE OC 12G", 4099, 88, 200, 261, "2K 高画质显卡"));
        seed(gpu("撼讯", "Radeon RX 7700 XT Hellhound 12G", 3199, 82, 245, 332, "2K 游戏显卡"));
        seed(gpu("蓝戟", "Intel Arc B580 Photon 12G OC", 1899, 68, 190, 272, "中端高性价比显卡"));

        seed(memory("金士顿", "FURY Beast 32GB DDR5 6000", 699, 82, "DDR5", "16GBx2 套条"));
        seed(memory("芝奇", "Ripjaws V 16GB DDR4 3200", 269, 58, "DDR4", "8GBx2 套条"));
        seed(memory("宏碁", "Predator 32GB DDR4 3600", 459, 66, "DDR4", "16GBx2 套条"));
        seed(memory("光威", "天策 32GB DDR5 6400", 599, 80, "DDR5", "16GBx2 套条"));
        seed(memory("海盗船", "Vengeance 32GB DDR5 6000", 729, 82, "DDR5", "16GBx2 套条"));
        seed(memory("金百达", "银爵 32GB DDR4 3600", 399, 65, "DDR4", "16GBx2 套条"));

        seed(storage("西部数据", "WD_BLACK SN770 1TB", 429, 78, "PCIe 4.0 NVMe SSD"));
        seed(storage("三星", "990 EVO 1TB", 599, 84, "PCIe 4.0 NVMe SSD"));
        seed(storage("铠侠", "EXCERIA PLUS G3 1TB", 399, 74, "PCIe 4.0 NVMe SSD"));
        seed(storage("致态", "TiPlus7100 2TB", 799, 86, "PCIe 4.0 NVMe SSD"));
        seed(storage("宏碁", "GM7 1TB", 389, 76, "PCIe 4.0 NVMe SSD"));

        seed(psu("长城", "猎金部落 650W 金牌", 469, 75, 650, "650W 金牌全模组"));
        seed(psu("海韵", "FOCUS GX-750 金牌", 699, 86, 750, "750W 金牌全模组"));
        seed(psu("振华", "LEADEX G 850W 金牌", 799, 90, 850, "850W 金牌全模组"));
        seed(psu("微星", "MAG A650BN 650W 铜牌", 329, 65, 650, "650W 铜牌电源"));
        seed(psu("酷冷至尊", "GX III 750W 金牌", 599, 82, 750, "ATX 3.0 金牌电源"));
        seed(psu("先马", "黑钻 850W 金牌", 529, 78, 850, "850W 金牌电源"));

        seed(pcCase("乔思伯", "D31 Mesh", 399, 72, "M-ATX", 330, 168, "M-ATX,ITX", "紧凑型网孔机箱"));
        seed(pcCase("NZXT", "H5 Flow", 599, 78, "ATX", 365, 165, "ATX,M-ATX,ITX", "中塔风道机箱"));
        seed(pcCase("追风者", "P360A", 399, 72, "ATX", 400, 160, "ATX,M-ATX,ITX", "高风道中塔机箱"));
        seed(pcCase("先马", "趣造 Air", 299, 66, "M-ATX", 335, 160, "M-ATX,ITX", "紧凑 M-ATX 机箱"));
        seed(pcCase("联力", "A3-mATX", 549, 80, "M-ATX", 415, 165, "M-ATX,ITX", "高质感 M-ATX 机箱"));

        seed(cooler("利民", "Peerless Assassin 120 SE", 169, 80, 155, "双塔风冷"));
        seed(cooler("ID-COOLING", "IS-55", 139, 58, 55, "下压式散热，适合小机箱"));
        seed(cooler("雅浚", "B3 PRO", 99, 58, 153, "入门塔式风冷"));
        seed(cooler("九州风神", "冰立方 AK400", 149, 68, 155, "主流单塔风冷"));
        seed(cooler("利民", "FROZEN MAGIC 360", 399, 88, 52, "360 水冷，机箱需支持冷排"));
    }

    private void seed(HardwarePart part) {
        if (hardwarePartRepository.existsByBrandAndModel(part.getBrand(), part.getModel())) {
            return;
        }
        hardwarePartRepository.save(part);
    }

    private void normalizeOldGpuBrands() {
        hardwarePartRepository.findByBrandAndModel("NVIDIA", "GeForce RTX 4060 Ti 8G")
                .ifPresent(part -> part.update(HardwarePartType.GPU, "七彩虹", "iGame GeForce RTX 4060 Ti Ultra W 8G", 2799, 78, "", "", "", 160, 245, 0, "", "2K 入门游戏显卡", true));
        hardwarePartRepository.findByBrandAndModel("NVIDIA", "GeForce RTX 4070 SUPER 12G")
                .ifPresent(part -> part.update(HardwarePartType.GPU, "华硕", "DUAL GeForce RTX 4070 SUPER O12G", 4599, 92, "", "", "", 220, 267, 0, "", "2K 高刷和轻量 4K", true));
        hardwarePartRepository.findByBrandAndModel("AMD", "Radeon RX 7800 XT 16G")
                .ifPresent(part -> part.update(HardwarePartType.GPU, "蓝宝石", "PURE Radeon RX 7800 XT 16G", 3899, 88, "", "", "", 263, 280, 0, "", "大显存，适合 2K 高画质", true));
    }

    private HardwarePart cpu(String brand, String model, int price, int score, String socket, int wattage, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.CPU, brand, model, price, score);
        part.update(HardwarePartType.CPU, brand, model, price, score, socket, "", "", wattage, 0, 0, "", specs, true);
        return part;
    }

    private HardwarePart motherboard(String brand, String model, int price, int score, String socket, String memory, String formFactor, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.MOTHERBOARD, brand, model, price, score);
        part.update(HardwarePartType.MOTHERBOARD, brand, model, price, score, socket, memory, formFactor, 0, 0, 0, "", specs, true);
        return part;
    }

    private HardwarePart gpu(String brand, String model, int price, int score, int wattage, int length, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.GPU, brand, model, price, score);
        part.update(HardwarePartType.GPU, brand, model, price, score, "", "", "", wattage, length, 0, "", specs, true);
        return part;
    }

    private HardwarePart memory(String brand, String model, int price, int score, String memory, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.MEMORY, brand, model, price, score);
        part.update(HardwarePartType.MEMORY, brand, model, price, score, "", memory, "", 0, 0, 0, "", specs, true);
        return part;
    }

    private HardwarePart storage(String brand, String model, int price, int score, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.STORAGE, brand, model, price, score);
        part.update(HardwarePartType.STORAGE, brand, model, price, score, "", "", "", 0, 0, 0, "", specs, true);
        return part;
    }

    private HardwarePart psu(String brand, String model, int price, int score, int wattage, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.PSU, brand, model, price, score);
        part.update(HardwarePartType.PSU, brand, model, price, score, "", "", "", wattage, 0, 0, "", specs, true);
        return part;
    }

    private HardwarePart pcCase(String brand, String model, int price, int score, String formFactor, int gpuLength, int coolerHeight, String supportedFormFactors, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.CASE, brand, model, price, score);
        part.update(HardwarePartType.CASE, brand, model, price, score, "", "", formFactor, 0, gpuLength, coolerHeight, supportedFormFactors, specs, true);
        return part;
    }

    private HardwarePart cooler(String brand, String model, int price, int score, int height, String specs) {
        HardwarePart part = new HardwarePart(HardwarePartType.COOLER, brand, model, price, score);
        part.update(HardwarePartType.COOLER, brand, model, price, score, "", "", "", 0, 0, height, "", specs, true);
        return part;
    }
}
