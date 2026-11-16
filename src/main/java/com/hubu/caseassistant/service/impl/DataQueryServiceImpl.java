package com.hubu.caseassistant.service.impl;

import com.hubu.caseassistant.service.DataQueryService;
import com.hubu.caseassistant.service.client.DifyApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataQueryServiceImpl implements DataQueryService {

    private final DifyApiClient difyApiClient;
    private static final String DATA_DIRECTORY = "src/main/resources/data/";

    @Override
    public String answerQuestion(String question) {
        // Dify的Prompt应该使用简洁的表名，如 'cases', 'quality_evaluations' 等
        String generatedSql = difyApiClient.generateSql(question);

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:");
             Statement stmt = conn.createStatement()) {

            // 在执行查询前，将全部5个CSV文件加载到DuckDB内存中
            log.info("Loading all 5 CSV files into DuckDB memory...");
            loadCsvIntoDuckDbTable(conn, "cases", DATA_DIRECTORY + "aj_yx_aj.csv");
            loadCsvIntoDuckDbTable(conn, "case_summaries", DATA_DIRECTORY + "aqzy.csv");
            loadCsvIntoDuckDbTable(conn, "quality_evaluations", DATA_DIRECTORY + "zlpc_yx_aj.csv");
            loadCsvIntoDuckDbTable(conn, "quality_evaluation_logs", DATA_DIRECTORY + "zlpc_czjl.csv");
            loadCsvIntoDuckDbTable(conn, "evaluation_templates", DATA_DIRECTORY + "pcmb.csv");
            log.info("All CSV files loaded successfully into in-memory tables.");

            log.info("Executing SQL with DuckDB: {}", generatedSql);
            ResultSet rs = stmt.executeQuery(generatedSql);

            List<Map<String, Object>> queryResult = resultSetToList(rs);
            log.info("DuckDB SQL query result: {}", queryResult);

            if (queryResult.isEmpty()) {
                return "根据您的文件查询，没有找到相关数据。";
            }
            if (queryResult.size() == 1 && queryResult.get(0).size() == 1) {
                return "查询结果为：" + queryResult.get(0).values().iterator().next();
            }
            return "查询结果如下：" + queryResult.toString();

        } catch (Exception e) {
            log.error("使用DuckDB查询CSV时出错", e);
            return "执行查询时发生错误：" + e.getMessage();
        }
    }

    /**
     * 使用Apache Commons CSV读取文件并加载为DuckDB内存表
     */
    private void loadCsvIntoDuckDbTable(Connection conn, String tableName, String filePath) throws Exception {
        // 使用try-with-resources确保Reader和Parser被正确关闭
        try (Reader reader = new FileReader(filePath, StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setIgnoreHeaderCase(true).setTrim(true).build());
             Statement stmt = conn.createStatement()) {

            List<String> headers = csvParser.getHeaderNames();

            // 创建或替换表
            StringBuilder createTableSql = new StringBuilder("CREATE OR REPLACE TABLE " + tableName + " (");
            for (String header : headers) {
                // 使用双引号处理可能包含特殊字符或关键字的列名
                createTableSql.append("\"").append(header).append("\" VARCHAR, ");
            }
            createTableSql.setLength(createTableSql.length() - 2); // 移除最后的 ", "
            createTableSql.append(")");
            stmt.execute(createTableSql.toString());
            log.debug("In-memory table created or replaced: {}", tableName);

            // 批量插入数据，提高效率
            final int batchSize = 1000;
            int count = 0;
            StringBuilder insertSqlPrefix = new StringBuilder("INSERT INTO " + tableName + " VALUES ");
            StringBuilder batchValues = new StringBuilder();

            for (CSVRecord csvRecord : csvParser) {
                batchValues.append("(");
                for (String header : headers) {
                    // 处理单引号，防止SQL注入（虽然这里是内部数据，但仍是好习惯）
                    String value = csvRecord.get(header).replace("'", "''");
                    batchValues.append("'").append(value).append("', ");
                }
                batchValues.setLength(batchValues.length() - 2);
                batchValues.append("), ");

                count++;
                if (count % batchSize == 0) {
                    batchValues.setLength(batchValues.length() - 2); // 移除最后的 ", "
                    stmt.execute(insertSqlPrefix.toString() + batchValues.toString());
                    batchValues.setLength(0); // 重置
                }
            }
            // 插入剩余的批次
            if (batchValues.length() > 0) {
                batchValues.setLength(batchValues.length() - 2);
                stmt.execute(insertSqlPrefix.toString() + batchValues.toString());
            }
            log.debug("Loaded {} records into table {}.", count, tableName);
        }
    }

    // 辅助方法：将ResultSet转换为List<Map>
    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        int columnCount = rs.getMetaData().getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
            }
            list.add(row);
        }
        return list;
    }
}