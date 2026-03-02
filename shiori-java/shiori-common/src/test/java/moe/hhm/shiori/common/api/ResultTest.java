package moe.hhm.shiori.common.api;

import moe.hhm.shiori.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @Test
    void shouldCreateSuccessResult() {
        long before = System.currentTimeMillis();
        Result<String> result = Result.success("ok");
        long after = System.currentTimeMillis();

        assertThat(result.code()).isEqualTo(0);
        assertThat(result.message()).isEqualTo("成功");
        assertThat(result.data()).isEqualTo("ok");
        assertThat(result.timestamp()).isBetween(before, after);
    }

    @Test
    void shouldCreateFailureFromErrorCode() {
        Result<Object> result = Result.failure(CommonErrorCode.INVALID_PARAM);

        assertThat(result.code()).isEqualTo(CommonErrorCode.INVALID_PARAM.code());
        assertThat(result.message()).isEqualTo(CommonErrorCode.INVALID_PARAM.message());
        assertThat(result.data()).isNull();
    }

    @Test
    void shouldCreateFailureWithCustomValues() {
        Result<String> result = Result.failure(12345, "自定义错误", "data");

        assertThat(result.code()).isEqualTo(12345);
        assertThat(result.message()).isEqualTo("自定义错误");
        assertThat(result.data()).isEqualTo("data");
    }
}
