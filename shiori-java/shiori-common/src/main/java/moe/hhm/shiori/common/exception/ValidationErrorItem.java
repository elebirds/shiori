package moe.hhm.shiori.common.exception;

public record ValidationErrorItem(String field, String message, Object rejectedValue) {
}
