package moe.hhm.shiori.social.model;

public class PostEntity {
    private Long id;
    private String postNo;
    private Long authorUserId;
    private String sourceType;
    private String contentHtml;
    private Long relatedProductId;
    private String relatedProductNo;
    private String relatedProductTitle;
    private String relatedProductCoverObjectKey;
    private Long relatedProductMinPriceCent;
    private Long relatedProductMaxPriceCent;
    private String relatedProductCampusCode;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPostNo() {
        return postNo;
    }

    public void setPostNo(String postNo) {
        this.postNo = postNo;
    }

    public Long getAuthorUserId() {
        return authorUserId;
    }

    public void setAuthorUserId(Long authorUserId) {
        this.authorUserId = authorUserId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getContentHtml() {
        return contentHtml;
    }

    public void setContentHtml(String contentHtml) {
        this.contentHtml = contentHtml;
    }

    public Long getRelatedProductId() {
        return relatedProductId;
    }

    public void setRelatedProductId(Long relatedProductId) {
        this.relatedProductId = relatedProductId;
    }

    public String getRelatedProductNo() {
        return relatedProductNo;
    }

    public void setRelatedProductNo(String relatedProductNo) {
        this.relatedProductNo = relatedProductNo;
    }

    public String getRelatedProductTitle() {
        return relatedProductTitle;
    }

    public void setRelatedProductTitle(String relatedProductTitle) {
        this.relatedProductTitle = relatedProductTitle;
    }

    public String getRelatedProductCoverObjectKey() {
        return relatedProductCoverObjectKey;
    }

    public void setRelatedProductCoverObjectKey(String relatedProductCoverObjectKey) {
        this.relatedProductCoverObjectKey = relatedProductCoverObjectKey;
    }

    public Long getRelatedProductMinPriceCent() {
        return relatedProductMinPriceCent;
    }

    public void setRelatedProductMinPriceCent(Long relatedProductMinPriceCent) {
        this.relatedProductMinPriceCent = relatedProductMinPriceCent;
    }

    public Long getRelatedProductMaxPriceCent() {
        return relatedProductMaxPriceCent;
    }

    public void setRelatedProductMaxPriceCent(Long relatedProductMaxPriceCent) {
        this.relatedProductMaxPriceCent = relatedProductMaxPriceCent;
    }

    public String getRelatedProductCampusCode() {
        return relatedProductCampusCode;
    }

    public void setRelatedProductCampusCode(String relatedProductCampusCode) {
        this.relatedProductCampusCode = relatedProductCampusCode;
    }
}
