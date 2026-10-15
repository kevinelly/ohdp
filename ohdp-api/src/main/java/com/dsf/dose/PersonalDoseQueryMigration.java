package com.dsf.dose;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

/** Backfill searchable metadata without changing approved payloads, bytes or optimistic versions. */
@Component @RequiredArgsConstructor
public class PersonalDoseQueryMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    @Override @Transactional public void run(ApplicationArguments args) throws Exception {
        for(var row:jdbc.queryForList("select id,payload,history from personal_dose_case where query_indexed is null or query_indexed=false")) {
            var d=mapper.readTree((String)row.get("payload"));
            LocalDate date=null;String value=d.path("inspectionDate").asText("");
            if(!value.isBlank())try{date=LocalDate.parse(value);}catch(java.time.format.DateTimeParseException ignored){/* Legacy invalid dates remain excluded from date filters. */}
            LocalDateTime generated=null;String actor=null;
            for(var event:mapper.readTree((String)row.get("history")))if(event.path("action").asText().equals("archive")) {generated=LocalDateTime.parse(event.path("at").asText());actor=event.path("actor").asText();}
            jdbc.update("update personal_dose_case set inspection_date=?,city=?,county=?,query_indexed=true,generated_at=?,generated_by=? where id=?",date,d.path("city").asText(""),d.path("county").asText(""),generated,actor,row.get("id"));
        }
    }
}
