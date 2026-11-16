package com.hubu.caseassistant.service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.hubu.caseassistant.dto.lcba.AjwsjzxxData;
import com.hubu.caseassistant.inference.GetAJWSJZWJDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * 写死 bmsah 和 jzwjh 的 HttpClient 实现
 */
@Slf4j
@Service
public class SupconFeignService {

    private static final String LIST_URL = "http://143.128.1.18:8181/tyyw/GetAJWSJZXX";
    private static final String FILE_URL = "http://143.128.1.18:8181/tyyw/GetAJWSJZWJ";

    /** 写死的案号 */
    private static final String FIXED_BMSAH = "鄂襄检评查受[2024]420600000004号";

    /** 写死的 jzwjh 常量 */
    private static final String FIXED_JZWJH = "9C67462FE7A048E5A91214B96762DFDD";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 获取文书目录列表 (bmsah 已写死)
     */
    public List<AjwsjzxxData> getCaseDocumentList(String bmsahUnused) {
        log.info("【HttpClient】获取文书列表，使用写死案号: {}", FIXED_BMSAH);
        try {
            String body = mapper.writeValueAsString(Map.of("bmsah", FIXED_BMSAH));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(LIST_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("【HttpClient LIST 原始响应】：{}", resp.body());

            Map<?, ?> root = mapper.readValue(resp.body(), Map.class);
            if ("0".equals(String.valueOf(root.get("code")))) {
                List<?> raw = (List<?>) root.get("data");
                return raw.stream()
                        .map(o -> mapper.convertValue(o, AjwsjzxxData.class))
                        .filter(item -> "N".equals(item.getSfml())
                                && item.getJzwjh() != null
                                && item.getWjmc() != null)
                        .collect(Collectors.toList());
            } else {
                log.warn("接口业务失败: {}", root.get("message"));
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("调用 GetAJWSJZXX 异常", e);
            return Collections.emptyList();
        }
    }



    /**
     * 下载单个文书文件（bmsah 和 jzwjh 均已写死）
     * 2. 修改方法签名，不再需要任何参数
     */
    @Retryable(
            value = {java.net.SocketTimeoutException.class}, // 遇到哪些异常才重试
            maxAttempts = 5,                                 // 最多 3 次（含首次）
            backoff = @Backoff(delay = 1500)                 // 间隔 1.5 秒
    )
    public byte[] downloadCaseDocument(String bmsah1,String jzwjh1) {
        // 3. 修改日志，使用写死的常量
        log.info("【HttpClient】使用写死案号：{}，及写死文件号：{}", bmsah1, jzwjh1);

        try {
            // --- 诊断开始 ---
            log.info("==================== 参数深度诊断开始 ====================");

            /* ---------- 2. 打日志 ---------- */
            log.info("【HttpClient】净化后案号：{}，文件号：{}", bmsah1, jzwjh1);


            log.info("==================== 参数深度诊断结束 ====================");
            // --- 诊断结束 ---
            // 4. 修改请求体，使用写死的常量
            Gson gson = new Gson();
            String body = gson.toJson(Map.of("bmsah", FIXED_BMSAH, "jzwjh", FIXED_JZWJH));
//            String body = mapper.writeValueAsString(Map.of("bmsah", bmsah1, "jzwjh", jzwjh1));
            log.info(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(FILE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            String responseBody = resp.body();
            log.info("【HttpClient FILE 原始响应】：{}", responseBody);

            if (responseBody != null && !responseBody.isBlank()) {
                if (Pattern.matches("^[A-Za-z0-9+/=\\r\\n]+$", responseBody.trim())) {
                    log.info("响应内容符合Base64格式，正在解码...");
                    return Base64.getDecoder().decode(responseBody.trim().replaceAll("\\s", ""));
                } else if (responseBody.trim().startsWith("{")) {
                    log.info("响应内容为JSON格式，正在解析...");
                    Map<?, ?> root = mapper.readValue(responseBody, Map.class);
                    if ("0".equals(String.valueOf(root.get("code")))) {
                        GetAJWSJZWJDTO dto = mapper.convertValue(root.get("data"), GetAJWSJZWJDTO.class);
                        if (dto.getWjl() != null && !dto.getWjl().isEmpty()) {
                            log.info("成功从JSON中下载文件 [{}], 大小: {}B", dto.getWjmc(), dto.getWjl().length());
                            return Base64.getDecoder().decode(dto.getWjl());
                        }
                    } else {
                        log.error("从LCBA下载文件失败。响应内容: {}", responseBody);
                    }
                } else {
                    log.error("从LCBA下载文件失败，服务器返回的既不是有效的Base64，也不是成功的JSON。响应内容: {}", responseBody);
                }
                return null;
            }

            log.warn("从LCBA下载文件成功，但文件内容为空。");
            return null;

        } catch (Exception e) {
            log.error("调用 GetAJWSJZWJ 异常", e);
            return null;
        }
    }

}