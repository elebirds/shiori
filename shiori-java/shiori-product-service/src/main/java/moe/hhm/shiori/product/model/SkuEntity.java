package moe.hhm.shiori.product.model;

public class SkuEntity {
    private Long id;
    private Long productId;
    private String skuNo;
    private String displayName;
    private String specItemsJson;
    private String specSignature;
    private String skuName;
    private String specJson;
    private Long priceCent;
    private Integer stock;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getSkuNo() {
        return skuNo;
    }

    public void setSkuNo(String skuNo) {
        this.skuNo = skuNo;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSpecItemsJson() {
        return specItemsJson;
    }

    public void setSpecItemsJson(String specItemsJson) {
        this.specItemsJson = specItemsJson;
    }

    public String getSpecSignature() {
        return specSignature;
    }

    public void setSpecSignature(String specSignature) {
        this.specSignature = specSignature;
    }

    public String getSkuName() {
        return skuName;
    }

    public void setSkuName(String skuName) {
        this.skuName = skuName;
    }

    public String getSpecJson() {
        return specJson;
    }

    public void setSpecJson(String specJson) {
        this.specJson = specJson;
    }

    public Long getPriceCent() {
        return priceCent;
    }

    public void setPriceCent(Long priceCent) {
        this.priceCent = priceCent;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }
}
