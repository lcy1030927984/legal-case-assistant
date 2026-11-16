package com.hubu.caseassistant.dto;

import lombok.Data;

/**
 * 统一API响应结果封装类
 * @param <T> 响应数据的类型
 */
@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    // 私有化构造函数，强制使用静态方法创建实例
    private ApiResponse() {}

    /**
     * 成功响应的静态工厂方法
     * @param data 响应数据
     * @return ApiResponse 实例
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("操作成功");
        response.setData(data);
        return response;
    }

    /**
     * 异步任务已接受的响应
     * @param data 响应数据
     * @return ApiResponse 实例
     */
    public static <T> ApiResponse<T> accepted(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(202); // HTTP状态码202: Accepted
        response.setMessage("任务已接受，正在处理中");
        response.setData(data);
        return response;
    }

    /**
     * 错误响应的静态工厂方法
     * @param code 错误码
     * @param message 错误信息
     * @return ApiResponse 实例
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        response.setData(null);
        return response;
    }
}