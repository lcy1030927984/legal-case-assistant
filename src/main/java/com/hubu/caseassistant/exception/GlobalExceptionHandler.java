package com.hubu.caseassistant.exception;

import com.hubu.caseassistant.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice // 这是一个全局异常处理组件
public class GlobalExceptionHandler {

    /**
     * 捕获所有未被特定处理的Exception异常
     * @param ex 异常对象
     * @return 包含错误信息的ResponseEntity
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGlobalException(Exception ex) {
        // 记录详细的错误日志，这对于生产环境排错至关重要
        log.error("服务器发生未知错误: ", ex);

        // 向前端返回一个通用的、安全的错误信息
        ApiResponse<String> errorResponse = ApiResponse.error(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "服务器内部错误，请联系管理员"
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}