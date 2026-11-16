package com.hubu.caseassistant.dto.lcba;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;  
import lombok.Data;  
import java.util.List;

@Data  
@JsonIgnoreProperties(ignoreUnknown = true)
public class AjwsjzxxData {  
    private String jzmlmc; // 卷宗目录名称  
    private String jzwjh;  // 卷宗文件号 (下载时需要)  
    private String wjmc;   // 文件名称  
    private String wjkzm;  // 文件扩展名  
    private String sfml;   // 是否目录 ("1"表示是目录, "0"表示是文件)  
    private List<AjwsjzxxData> children; // 子集
}