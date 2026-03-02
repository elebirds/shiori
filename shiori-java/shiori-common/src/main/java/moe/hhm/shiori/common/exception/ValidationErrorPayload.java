package moe.hhm.shiori.common.exception;

import java.util.List;

public record ValidationErrorPayload(List<ValidationErrorItem> errors) {
}
