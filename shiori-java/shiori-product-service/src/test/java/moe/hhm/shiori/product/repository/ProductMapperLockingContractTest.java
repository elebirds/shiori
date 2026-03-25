package moe.hhm.shiori.product.repository;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class ProductMapperLockingContractTest {

    @Test
    void shouldExposeLockedSkuLookupForStockOperations() {
        final Method[] holder = new Method[1];

        assertThatNoException().isThrownBy(() ->
                holder[0] = ProductMapper.class.getMethod("findActiveSkuByIdForUpdate", Long.class)
        );

        Select select = holder[0].getAnnotation(Select.class);
        assertThat(select).isNotNull();
        assertThat(String.join("\n", select.value())).contains("FOR UPDATE");
    }
}
