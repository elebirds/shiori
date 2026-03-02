package moe.hhm.shiori.common.exception;

import moe.hhm.shiori.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class BizExceptionTest {

    @Test
    void shouldKeepCodeStatusAndExtraData() {
        BizException ex = new BizException(CommonErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "detail");

        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getExtraData()).isEqualTo("detail");
        assertThat(ex.getMessage()).isEqualTo(CommonErrorCode.NOT_FOUND.message());
    }
}
