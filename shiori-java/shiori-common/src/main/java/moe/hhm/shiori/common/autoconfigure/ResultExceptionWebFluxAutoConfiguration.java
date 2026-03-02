package moe.hhm.shiori.common.autoconfigure;

import tools.jackson.databind.ObjectMapper;
import moe.hhm.shiori.common.webflux.GlobalWebFluxErrorHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(name = "org.springframework.boot.webflux.error.ErrorWebExceptionHandler")
public class ResultExceptionWebFluxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalWebFluxErrorHandler globalWebFluxErrorHandler(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new GlobalWebFluxErrorHandler(objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }
}
