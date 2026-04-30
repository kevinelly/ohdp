package com.dsf.ohdp.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public class LinearRegressionUtil {

    private LinearRegressionUtil() {
    }

    public record RegressionResult(BigDecimal intercept, BigDecimal slope, BigDecimal correlation) {}

    /**
     * 最小二乘线性回归 y = a + bx
     *
     * @param points 数据点列表，每个 double[] 为 {x, y}，x 为浓度，y 为吸光度差
     * @return 包含截距a、斜率b、相关系数R的结果
     */
    public static RegressionResult calculate(List<double[]> points) {
        int n = points.size();
        if (n < 2) throw new IllegalArgumentException("至少需要2个数据点");
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (double[] p : points) {
            sumX += p[0];
            sumY += p[1];
            sumXY += p[0] * p[1];
            sumX2 += p[0] * p[0];
            sumY2 += p[1] * p[1];
        }
        double b = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double a = (sumY - b * sumX) / n;
        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        double r = denominator == 0 ? 0 : numerator / denominator;
        MathContext mc = new MathContext(10, RoundingMode.HALF_UP);
        return new RegressionResult(
                BigDecimal.valueOf(a).round(mc),
                BigDecimal.valueOf(b).round(mc),
                BigDecimal.valueOf(r).round(mc));
    }
}
