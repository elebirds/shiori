package moe.hhm.shiori.product.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "chat.ticket")
public class ChatTicketProperties {

    private String issuer = "shiori-chat-ticket";
    private long ttlSeconds = 300;
    private String kid;
    private String privateKeyPemBase64;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public String getKid() {
        return kid;
    }

    public void setKid(String kid) {
        this.kid = kid;
    }

    public String getPrivateKeyPemBase64() {
        return privateKeyPemBase64;
    }

    public void setPrivateKeyPemBase64(String privateKeyPemBase64) {
        this.privateKeyPemBase64 = privateKeyPemBase64;
    }
}
