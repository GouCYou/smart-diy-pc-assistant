package cn.cctstudio.smartdiypcassistantbackend.buildconfig;

public record BuildPartSelection(
        Long hardwarePartId,
        String title,
        Integer price,
        String image,
        String link,
        String skuId,
        String matchedModel,
        String priceType,
        String priceLabel,
        String priceConditions
) {
    public BuildPartSelection(Long hardwarePartId, String title, Integer price, String image, String link, String skuId, String matchedModel) {
        this(hardwarePartId,title,price,image,link,skuId,matchedModel,"","","");
    }
}
