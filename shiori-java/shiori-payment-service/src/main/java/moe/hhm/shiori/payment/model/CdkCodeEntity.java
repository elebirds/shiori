package moe.hhm.shiori.payment.model;

import java.time.LocalDateTime;

public class CdkCodeEntity {
    private Long batchId;
    private String codeHash;
    private String codeMask;
    private Long amountCent;
    private LocalDateTime expireAt;

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public String getCodeMask() {
        return codeMask;
    }

    public void setCodeMask(String codeMask) {
        this.codeMask = codeMask;
    }

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }
}
