package com.dsf.radiology;
import com.dsf.radiology.service.RadiologyCalculationEngine;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class RadiologyPrecisionBoundaryTest {
 private final RadiologyCalculationEngine engine=new RadiologyCalculationEngine();
 @Test void exposureTimeUsesReadingsNotNominalPrecision(){
  var n=new HashMap<String,BigDecimal>();n.put("__GROUP_COUNT",BigDecimal.ONE);
  n.put("ETD1-0",new BigDecimal("10"));
  for(int i=1;i<=3;i++)n.put("ETD1-"+i,new BigDecimal("10.12"));
  var r=engine.calculate("ETD",n,Map.of(),Map.of());
  assertThat(r.complete()).isTrue();
  assertThat(r.fields().stream().filter(f->f.code().equals("MEAN_1")).findFirst().orElseThrow().displayValue()).isEqualTo("10.12");
 }
 @Test void aecMeansKeepTheirOwnGroupsPrecision(){
  var n=new HashMap<String,BigDecimal>();
  for(int i=1;i<=5;i++){n.put("ARS1-"+i,new BigDecimal("1.234"));n.put("ARS2-"+i,new BigDecimal("1.2"));}
  var r=engine.calculate("ARS",n,Map.of(),Map.of());
  assertThat(r.complete()).isTrue();
  assertThat(r.fields().stream().filter(f->f.code().equals("MEAN_1")).findFirst().orElseThrow().displayValue()).isEqualTo("1.234");
  assertThat(r.fields().stream().filter(f->f.code().equals("MEAN_2")).findFirst().orElseThrow().displayValue()).isEqualTo("1.2");
 }
}
