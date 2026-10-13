package com.dsf.common.config;

import com.dsf.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Imports a business-only snapshot, records ownership, and never clears whole business tables. */
@Service
@Profile("desktop")
public class DesktopSampleDataService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final DataSource source;
    private final ObjectMapper mapper;
    private final DesktopMaintenanceGate gate;
    private final Path dataHome;
    private final Path archivePath;
    private boolean schemaReady;
    private static final List<String> TABLES = List.of(
        "ohdp_region", "ohdp_company_profile", "ohdp_detect_type", "ohdp_detection_method",
        "ohdp_device", "ohdp_dictionary_item", "ohdp_factor_template", "ohdp_hazard_factor",
        "ohdp_hazard_factor_limit", "ohdp_hazard_factor_method", "ohdp_law", "ohdp_standard",
        "ohdp_org_config", "ohdp_position_template", "ohdp_staff_profile", "ohdp_report_text_template",
        "ohdp_report_template_catalog", "ohdp_sds", "ohdp_reference_document_version", "ohdp_project",
        "ohdp_project_position", "ohdp_project_position_rel", "ohdp_result_item", "ohdp_lab_task",
        "ohdp_lab_task_sample", "ohdp_lab_result", "ohdp_lab_curve_point", "ohdp_report_file",
        "ohdp_report_operation_log", "qc_acceptance_rule", "qc_inspection_case", "qc_measurement",
        "qc_protection_point", "qc_attachment", "qc_report");
    private static final Map<String, List<String>> KEYS = Map.ofEntries(
        Map.entry("ohdp_company_profile", List.of("company_name")),
        Map.entry("ohdp_detect_type", List.of("type_code")),
        Map.entry("ohdp_detection_method", List.of("method_code")),
        Map.entry("ohdp_dictionary_item", List.of("dict_type", "item_code")),
        Map.entry("ohdp_region", List.of("region_code")),
        Map.entry("ohdp_standard", List.of("standard_code")),
        Map.entry("ohdp_project", List.of("project_code")),
        Map.entry("ohdp_lab_task", List.of("task_code")),
        Map.entry("qc_inspection_case", List.of("case_code")),
        Map.entry("qc_acceptance_rule", List.of("modality", "item_code", "rule_version")));

    public DesktopSampleDataService(DataSource source, PlatformTransactionManager manager, ObjectMapper mapper, DesktopMaintenanceGate gate,
            @Value("${BHFYZX_DATA_HOME}") String dataHome,
            @Value("${BHFYZX_SAMPLE_ZIP:}") String archivePath) {
        this.source = source;
        this.db = new JdbcTemplate(source);
        this.tx = new TransactionTemplate(manager);
        this.mapper = mapper;
        this.gate = gate;
        this.dataHome = Path.of(dataHome).toAbsolutePath().normalize();
        this.archivePath = archivePath.isBlank() ? null : Path.of(archivePath);
    }

    public synchronized void ensureSchema() {
        if (schemaReady) return;
        db.execute("CREATE TABLE IF NOT EXISTS desktop_sample_row (table_name VARCHAR(100), row_id BIGINT, PRIMARY KEY(table_name,row_id))");
        db.execute("CREATE TABLE IF NOT EXISTS desktop_sample_file (file_path VARCHAR(1500) PRIMARY KEY)");
        db.execute("CREATE TABLE IF NOT EXISTS desktop_setting (setting_key VARCHAR(100) PRIMARY KEY, setting_value VARCHAR(2000))");
        schemaReady = true;
    }

    public synchronized Map<String, Object> status() {
        ensureSchema();
        long rows = db.queryForObject("SELECT COUNT(*) FROM desktop_sample_row", Long.class);
        long projects = db.queryForObject("SELECT COUNT(*) FROM ohdp_project p JOIN desktop_sample_row s ON s.table_name='ohdp_project' AND s.row_id=p.id WHERE p.deleted=false", Long.class);
        long cases = db.queryForObject("SELECT COUNT(*) FROM qc_inspection_case p JOIN desktop_sample_row s ON s.table_name='qc_inspection_case' AND s.row_id=p.id WHERE p.deleted=false", Long.class);
        return Map.of("imported", rows > 0, "rows", rows, "projects", projects, "cases", cases,
                "available", archivePath != null && Files.isRegularFile(archivePath));
    }

    public synchronized Map<String, Object> importData() {
        try (var scope = gate.maintenance()) { return importLocked(); }
    }

    private Map<String, Object> importLocked() {
        ensureSchema();
        if (Boolean.TRUE.equals(status().get("imported"))) return status();
        if (archivePath == null || !Files.isRegularFile(archivePath)) throw new BusinessException("安装包缺少测试数据文件。");
        List<Path> copied = new ArrayList<>();
        String batch = UUID.randomUUID().toString();
        try (ZipFile zip = new ZipFile(archivePath.toFile())) {
            JsonNode snapshot;
            try (InputStream in = zip.getInputStream(zip.getEntry("dataset.json"))) { snapshot = mapper.readTree(in); }
            if (snapshot.path("format").asInt() != 1) throw new BusinessException("测试数据格式不受支持。");
            Map<String, JsonNode> specs = new LinkedHashMap<>();
            for (JsonNode table : snapshot.path("tables")) {
                String name = table.path("name").asText();
                if (!TABLES.contains(name)) throw new BusinessException("测试数据包含不允许导入的表：" + name);
                specs.put(name, table);
            }
            Map<String, String> paths = new HashMap<>();
            for (JsonNode file : snapshot.path("files")) {
                String relative = file.path("path").asText();
                int slash = relative.indexOf('/');
                if (slash < 0 || !Set.of("reports", "reference-documents", "radiology").contains(relative.substring(0, slash)))
                    throw new BusinessException("测试附件目录不合法。");
                Path root = dataHome.resolve("storage").resolve(relative.substring(0, slash)).resolve("test-data").resolve(batch);
                Path target = root.resolve(relative.substring(slash + 1)).normalize();
                if (!target.startsWith(root)) throw new BusinessException("测试附件路径越界。");
                byte[] bytes;
                try (InputStream in = zip.getInputStream(zip.getEntry("files/" + relative))) { bytes = in.readAllBytes(); }
                if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(file.path("sha256").asText()))
                    throw new BusinessException("测试附件校验失败：" + relative);
                Files.createDirectories(target.getParent());
                Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
                copied.add(target);
                paths.put("/data/" + relative, target.toString());
            }
            tx.executeWithoutResult(transaction -> importRows(specs, paths, batch, snapshot.path("snapshotId").asText()));
            return status();
        } catch (Exception ex) {
            for (Path path : copied) { try { Files.deleteIfExists(path); } catch (Exception ignored) { } }
            if (ex instanceof BusinessException business) throw business;
            throw new BusinessException("导入测试数据失败，数据库已回滚：" + ex.getMessage());
        }
    }

    private void importRows(Map<String, JsonNode> specs, Map<String, String> paths, String batch, String snapshotId) {
        Map<String, Map<Long, Long>> ids = new HashMap<>();
        List<Imported> imported = new ArrayList<>();
        for (String table : TABLES) {
            JsonNode spec = specs.get(table);
            if (spec == null) continue;
            Map<String, Integer> types = columnTypes(table);
            ids.put(table, new HashMap<>());
            for (JsonNode sourceRow : spec.path("rows")) {
                ObjectNode row = ((ObjectNode) sourceRow).deepCopy();
                long oldId = row.path("id").asLong();
                boolean missing = false;
                for (String col : List.of("file_path", "storage_path")) {
                    if (row.hasNonNull(col)) {
                        String local = paths.get(row.path(col).asText());
                        if (local == null) { missing = true; break; }
                        row.put(col, local);
                    }
                }
                if (missing) continue;
                Long existing = existingId(table, row);
                if (existing != null && !Set.of("ohdp_project", "ohdp_lab_task", "qc_inspection_case").contains(table)) {
                    ids.get(table).put(oldId, existing);
                    continue;
                }
                if (existing != null) {
                    String key = KEYS.get(table).get(0);
                    row.put(key, row.path(key).asText().substring(0, Math.min(40, row.path(key).asText().length())) + "-T" + batch.substring(0, 8));
                }
                List<String> columns = new ArrayList<>();
                row.fieldNames().forEachRemaining(col -> { if (!col.equals("id") && types.containsKey(col)) columns.add(col); });
                String sql = "INSERT INTO " + q(table) + " (" + (table.equals("ohdp_org_config") ? "id," : "") + String.join(",", columns.stream().map(DesktopSampleDataService::q).toList())
                        + ") VALUES (" + (table.equals("ohdp_org_config") ? "1," : "") + String.join(",", Collections.nCopies(columns.size(), "?")) + ")";
                // Owners are inserted first. Cyclic optional references are repaired in a second pass.
                ObjectNode insertedRow = row.deepCopy();
                for (String col : columns) {
                    String target = reference(table, col, row);
                    if (target != null && row.hasNonNull(col)) {
                        Long mapped = ids.getOrDefault(target, Map.of()).get(row.path(col).asLong());
                        if (mapped == null) insertedRow.putNull(col); else insertedRow.put(col, mapped);
                    }
                }
                if (table.equals("ohdp_reference_document_version")) {
                    Integer next = db.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM ohdp_reference_document_version WHERE reference_type=? AND reference_id=?",
                            Integer.class, insertedRow.path("reference_type").asText(), insertedRow.path("reference_id").asLong());
                    insertedRow.put("version_no", Math.max(row.path("version_no").asInt(), next));
                }
                GeneratedKeyHolder key = new GeneratedKeyHolder();
                db.update(connection -> {
                    PreparedStatement statement = connection.prepareStatement(sql, new String[] { "id" });
                    for (int i = 0; i < columns.size(); i++) bind(statement, i + 1, insertedRow.get(columns.get(i)), types.get(columns.get(i)));
                    return statement;
                }, key);
                long newId = Objects.requireNonNull(key.getKey()).longValue();
                ids.get(table).put(oldId, newId);
                db.update("INSERT INTO desktop_sample_row(table_name,row_id) VALUES (?,?)", table, newId);
                imported.add(new Imported(table, newId, row));
            }
        }
        for (Imported record : imported) {
            record.row.fieldNames().forEachRemaining(col -> {
                String target = reference(record.table, col, record.row);
                if (target == null || !record.row.hasNonNull(col)) return;
                Long mapped = ids.getOrDefault(target, Map.of()).get(record.row.path(col).asLong());
                db.update("UPDATE " + q(record.table) + " SET " + q(col) + "=? WHERE id=?", mapped, record.id);
            });
        }
        for (String path : paths.values()) db.update("INSERT INTO desktop_sample_file(file_path) VALUES (?)", path);
        db.update("MERGE INTO desktop_setting KEY(setting_key) VALUES ('sample.snapshot',?)", snapshotId);
    }

    private Long existingId(String table, ObjectNode row) {
        if (table.equals("ohdp_org_config")) return db.query("SELECT id FROM ohdp_org_config ORDER BY id", (rs, n) -> rs.getLong(1)).stream().findFirst().orElse(null);
        List<String> keys = KEYS.get(table);
        if (keys == null || keys.stream().anyMatch(k -> !row.hasNonNull(k))) return null;
        String where = String.join(" AND ", keys.stream().map(k -> q(k) + "=?").toList());
        return db.query("SELECT id FROM " + q(table) + " WHERE " + where,
                (rs, n) -> rs.getLong(1), keys.stream().map(k -> row.get(k).asText()).toArray()).stream().findFirst().orElse(null);
    }

    public synchronized Map<String, Object> deleteData() {
        try (var scope = gate.maintenance()) { return deleteLocked(); }
    }

    private Map<String, Object> deleteLocked() {
        ensureSchema();
        // Database ownership checks and deletion occur atomically. Never TRUNCATE or reset a database.
        tx.executeWithoutResult(transaction -> {
            List<String> owners = new ArrayList<>(TABLES);
            owners.add("sys_user");
            for (String table : owners) {
                for (String col : columnTypes(table).keySet()) {
                    List<ObjectNode> variants = table.equals("ohdp_reference_document_version") && col.equals("reference_id")
                            ? List.of(mapper.createObjectNode().put("reference_type", "LAW"), mapper.createObjectNode().put("reference_type", "STANDARD"))
                            : List.of(mapper.createObjectNode());
                    for (ObjectNode variant : variants) {
                        String target = reference(table, col, variant);
                        if (target == null) continue;
                        String condition = variant.has("reference_type") ? " AND a.reference_type='" + variant.path("reference_type").asText() + "'" : "";
                        long count = db.queryForObject("SELECT COUNT(*) FROM " + q(table) + " a JOIN desktop_sample_row parent ON parent.table_name=? AND parent.row_id=a." + q(col)
                                + " LEFT JOIN desktop_sample_row own ON own.table_name=? AND own.row_id=a.id WHERE own.row_id IS NULL AND a.deleted=false" + condition,
                                Long.class, target, table);
                        if (count > 0) throw new BusinessException("测试数据被后来新增的业务记录引用，已取消删除以保留业务数据。请先解除引用：" + table + "." + col + "（" + count + " 条）");
                    }
                }
            }
            List<String> reversed = new ArrayList<>(TABLES);
            Collections.reverse(reversed);
            for (String table : reversed)
                db.update("DELETE FROM " + q(table) + " WHERE id IN (SELECT row_id FROM desktop_sample_row WHERE table_name=?)", table);
            db.update("DELETE FROM desktop_sample_row");
            db.update("DELETE FROM desktop_setting WHERE setting_key='sample.snapshot'");
        });
        int remainingFiles = 0;
        for (String value : db.queryForList("SELECT file_path FROM desktop_sample_file", String.class)) {
            Path path = Path.of(value).toAbsolutePath().normalize();
            if (!path.startsWith(dataHome.resolve("storage")) || !path.toString().contains("test-data")) {
                remainingFiles++; continue;
            }
            try {
                Files.deleteIfExists(path);
                db.update("DELETE FROM desktop_sample_file WHERE file_path=?", value);
            } catch (Exception ex) { remainingFiles++; }
        }
        Map<String, Object> result = new HashMap<>(status());
        result.put("pendingFiles", remainingFiles);
        return result;
    }

    private Map<String, Integer> columnTypes(String table) {
        Map<String, Integer> result = new LinkedHashMap<>();
        try (Connection connection = source.getConnection(); ResultSet columns = connection.getMetaData().getColumns(null, null, table, null)) {
            while (columns.next()) result.put(columns.getString("COLUMN_NAME"), columns.getInt("DATA_TYPE"));
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
        return result;
    }

    private static void bind(PreparedStatement statement, int index, JsonNode value, int type) throws SQLException {
        if (value == null || value.isNull()) { statement.setNull(index, type); return; }
        switch (type) {
            case Types.BOOLEAN, Types.BIT -> statement.setBoolean(index, value.isBoolean() ? value.asBoolean() : value.asInt() != 0);
            case Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT -> statement.setLong(index, value.asLong());
            case Types.DECIMAL, Types.NUMERIC, Types.DOUBLE, Types.FLOAT, Types.REAL -> statement.setBigDecimal(index, value.decimalValue());
            case Types.DATE -> statement.setDate(index, java.sql.Date.valueOf(value.asText().substring(0, 10)));
            case Types.TIMESTAMP -> statement.setTimestamp(index, Timestamp.valueOf(value.asText().replace('T', ' ')));
            default -> statement.setString(index, value.isContainerNode() ? value.toString() : value.asText());
        }
    }

    private static String q(String value) {
        if (!value.matches("[a-z_][a-z0-9_]*")) throw new IllegalArgumentException("Invalid identifier");
        return "\"" + value + "\"";
    }

    private static String reference(String table, String col, JsonNode row) {
        return switch (col) {
            case "project_id" -> "ohdp_project";
            case "position_id" -> "ohdp_project_position";
            case "factor_id" -> "ohdp_hazard_factor";
            case "method_id" -> "ohdp_detection_method";
            case "task_id" -> "ohdp_lab_task";
            case "sample_id" -> "ohdp_lab_task_sample";
            case "lab_result_id" -> "ohdp_lab_result";
            case "company_profile_id", "entrust_unit_company_id", "submit_unit_company_id" -> "ohdp_company_profile";
            case "writer_staff_id", "reviewer_staff_id", "approver_staff_id", "employee_id" -> "ohdp_staff_profile";
            case "detect_type_dict_item_id" -> "ohdp_dictionary_item";
            case "task_origin_template_id", "evaluation_basis_template_id" -> "ohdp_report_text_template";
            case "detect_type_id" -> "ohdp_detect_type";
            case "parent_id" -> Set.of("ohdp_region", "ohdp_report_template_catalog").contains(table) ? table : null;
            case "case_id", "source_case_id" -> "qc_inspection_case";
            case "file_id" -> Set.of("ohdp_law", "ohdp_standard").contains(table) ? "ohdp_reference_document_version" : null;
            case "reference_id" -> row.path("reference_type").asText().equals("LAW") ? "ohdp_law" : "ohdp_standard";
            default -> null;
        };
    }

    private record Imported(String table, long id, ObjectNode row) { }
}
