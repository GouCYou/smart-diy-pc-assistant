package cn.cctstudio.smartdiypcassistantbackend.commerce;

import cn.cctstudio.smartdiypcassistantbackend.hardware.HardwarePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class JdProductSnapshotService {

    private final JdProductSnapshotRepository snapshotRepository;

    public JdProductSnapshotService(JdProductSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional
    public void recordProducts(List<JdProduct> products, String source, String rawKeyword) {
        if (products == null || products.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<JdProductSnapshot> snapshots = products.stream()
                .filter(this::hasSnapshotValue)
                .map(product -> new JdProductSnapshot(
                        product.hardwarePartId(),
                        product.type(),
                        product.skuId(),
                        product.title(),
                        product.price(),
                        product.image(),
                        product.link(),
                        product.shopName(),
                        product.matchedModel(),
                        true,
                        source,
                        rawKeyword,
                        now
                ))
                .toList();
        if (!snapshots.isEmpty()) {
            snapshotRepository.saveAll(snapshots);
        }
    }

    @Transactional
    public void recordUnavailable(HardwarePart part, String rawKeyword) {
        if (part == null || part.getId() == null) {
            return;
        }
        String skuId = "UNAVAILABLE-" + part.getId();
        snapshotRepository.save(new JdProductSnapshot(
                part.getId(),
                part.getType(),
                skuId,
                part.getModel() + " 京东暂无稳定可购商品",
                0,
                "",
                "",
                "",
                part.getModel(),
                false,
                "JD_SEARCH",
                rawKeyword,
                LocalDateTime.now()
        ));
    }

    private boolean hasSnapshotValue(JdProduct product) {
        return product != null
                && StringUtils.hasText(product.title())
                && StringUtils.hasText(product.skuId())
                && product.price() != null
                && product.price() > 0;
    }
}
