package com.anchor.api.controller;

import com.anchor.api.vo.ErrorVO;
import com.anchor.common.domain.AnchorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 把业务异常和参数校验失败转成统一的错误响应。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    /**
     * 按错误码映射 HTTP 状态。
     */
    @ExceptionHandler(AnchorException.class)
    public ResponseEntity<ErrorVO> handle(AnchorException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "NOT_FOUND", "UNKNOWN_CHAIN" -> HttpStatus.NOT_FOUND;
            case "CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ErrorVO(ex.getCode(), ex.getMessage()));
    }
    /**
     * 汇总字段校验失败的原因。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorVO> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ErrorVO("VALIDATION_ERROR", message));
    }
    /**
     * 未预期异常只对外返回内部错误，细节写入日志。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorVO> unknown(Exception ex) {
        log.error("接口出现未处理异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorVO("INTERNAL_ERROR", "内部错误"));
    }
}
