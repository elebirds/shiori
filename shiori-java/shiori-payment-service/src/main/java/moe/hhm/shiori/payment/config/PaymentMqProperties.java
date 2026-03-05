package moe.hhm.shiori.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payment.mq")
public class PaymentMqProperties {

    private boolean enabled = true;
    private String eventExchange = "shiori.payment.event";
    private String walletBalanceChangedRoutingKey = "wallet.balance.changed";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEventExchange() {
        return eventExchange;
    }

    public void setEventExchange(String eventExchange) {
        this.eventExchange = eventExchange;
    }

    public String getWalletBalanceChangedRoutingKey() {
        return walletBalanceChangedRoutingKey;
    }

    public void setWalletBalanceChangedRoutingKey(String walletBalanceChangedRoutingKey) {
        this.walletBalanceChangedRoutingKey = walletBalanceChangedRoutingKey;
    }
}
