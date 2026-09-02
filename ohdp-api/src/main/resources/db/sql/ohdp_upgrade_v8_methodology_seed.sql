INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '噪声', 'LEX8H', 'dB(A)', '85', 'dB(A)',
       '《工作场所物理因素测量 第8部分：噪声》（GBZ/T189.8-2007）',
       '仪器直读法',
       'ASV5910个体声暴露计：A-777、A-778、A-779、A-780；BSWA309倍频程声级计：A-832',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '噪声' AND deleted = 0);

INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '总粉尘', 'PE', 'mg/m3', '8', 'mg/m3',
       '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气中粉尘测定 第1部分：总粉尘浓度》(GBZ/T 192.1-2007)',
       '称重法',
       '（1）FCC-25型防爆采样器：A-906（2）CPA225D电子天平：A-522',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '总粉尘' AND deleted = 0);

INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '电焊烟尘', 'PE', 'mg/m3', '4', 'mg/m3',
       '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气中粉尘测定 第1部分：总粉尘浓度》(GBZ/T 192.1-2007)',
       '称重法',
       '（1）FCC-25型防爆采样器：A-906（2）CPA225D电子天平：A-522',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '电焊烟尘' AND deleted = 0);

INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '焊接烟尘', 'PE', 'mg/m3', '4', 'mg/m3',
       '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气中粉尘测定 第1部分：总粉尘浓度》(GBZ/T 192.1-2007)',
       '称重法',
       '（1）FCC-25型防爆采样器：A-906（2）CPA225D电子天平：A-522',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '焊接烟尘' AND deleted = 0);

INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '金属粉尘', 'PE', 'mg/m3', '8', 'mg/m3',
       '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气中粉尘测定 第1部分：总粉尘浓度》(GBZ/T 192.1-2007)',
       '称重法',
       '（1）FCC-25型防爆采样器：A-906（2）CPA225D电子天平：A-522',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '金属粉尘' AND deleted = 0);

INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '其他粉尘', 'PE', 'mg/m3', '8', 'mg/m3',
       '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气中粉尘测定 第1部分：总粉尘浓度》(GBZ/T 192.1-2007)',
       '称重法',
       '（1）FCC-25型防爆采样器：A-906（2）CPA225D电子天平：A-522',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '其他粉尘' AND deleted = 0);

INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '锰及其无机化合物', 'TWA', 'mg/m3', '0.15', 'mg/m3',
       '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气有毒物质测定 第17部分：锰及其化合物》(GBZ/T 300.17-2017)',
       '火焰原子吸收光谱法',
       '（1）FCC-3000G型防爆采样器：A-890（2）FCC-25型防爆采样器：A-907（3）ZEEnit700P原子吸收分光光度计：A-333',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '锰及其无机化合物' AND deleted = 0);

INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '苯', 'TWA', 'mg/m3', '6', 'mg/m3',
       '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气有毒物质测定 第66部分 苯、甲苯、二甲苯和乙苯》（GBZ/T 300.66-2017）',
       '溶剂解吸-气相色谱法',
       '（1）FCC-1500D型防爆采样器：A-820、A-819（2）GC2030气相色谱仪：A-694',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '苯' AND deleted = 0);

INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '甲苯', 'TWA', 'mg/m3', '50', 'mg/m3',
       '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气有毒物质测定 第66部分 苯、甲苯、二甲苯和乙苯》（GBZ/T 300.66-2017）',
       '溶剂解吸-气相色谱法',
       '（1）FCC-1500D型防爆采样器：A-820、A-819（2）GC2030气相色谱仪：A-694',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '甲苯' AND deleted = 0);

INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '二甲苯', 'TWA', 'mg/m3', '50', 'mg/m3',
       '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气有毒物质测定 第66部分 苯、甲苯、二甲苯和乙苯》（GBZ/T 300.66-2017）',
       '溶剂解吸-气相色谱法',
       '（1）FCC-1500D型防爆采样器：A-820、A-819（2）GC2030气相色谱仪：A-694',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '二甲苯' AND deleted = 0);

INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '乙苯', 'TWA', 'mg/m3', '100', 'mg/m3',
       '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气有毒物质测定 第66部分 苯、甲苯、二甲苯和乙苯》（GBZ/T 300.66-2017）',
       '溶剂解吸-气相色谱法',
       '（1）FCC-1500D型防爆采样器：A-820、A-819（2）GC2030气相色谱仪：A-694',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '乙苯' AND deleted = 0);

INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '丙酮', 'TWA', 'mg/m3', '300', 'mg/m3',
       '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气有毒物质测定 第59部分 挥发性有机化合物》（GBZ/T 300.59-2017）',
       '溶剂解吸-气相色谱法',
       '（1）FCC-1500D型防爆采样器（2）GC2030气相色谱仪',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '丙酮' AND deleted = 0);

INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '异丙醇', 'TWA', 'mg/m3', '350', 'mg/m3',
       '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气有毒物质测定 第59部分 挥发性有机化合物》（GBZ/T 300.59-2017）',
       '溶剂解吸-气相色谱法',
       '（1）FCC-1500D型防爆采样器（2）GC2030气相色谱仪',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '异丙醇' AND deleted = 0);

INSERT INTO ohdp_factor_template
    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit,
     detection_basis, detection_method, detection_instruments, deleted, created_at, updated_at)
SELECT '铅及其无机化合物', 'TWA', 'mg/m3', '0.05', 'mg/m3',
       '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气有毒物质测定 第18部分：铅及其化合物》(GBZ/T 300.18-2017)',
       '火焰原子吸收光谱法',
       '（1）FCC-3000G型防爆采样器（2）ZEEnit700P原子吸收分光光度计：A-333',
       0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ohdp_factor_template WHERE factor_name = '铅及其无机化合物' AND deleted = 0);
