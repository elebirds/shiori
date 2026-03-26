package moe.hhm.shiori.payment.repository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotatedSqlSanityTest {

    @Test
    void shouldNotLeaveXmlCDataMarkersInSelectAnnotations() {
        List<String> invalidMethods = Arrays.stream(PaymentMapper.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Select.class))
                .filter(this::containsXmlCDataMarker)
                .map(Method::toGenericString)
                .toList();

        assertThat(invalidMethods).isEmpty();
    }

    private boolean containsXmlCDataMarker(Method method) {
        Select select = method.getAnnotation(Select.class);
        return select != null && String.join("\n", select.value()).contains("<![CDATA[");
    }
}
