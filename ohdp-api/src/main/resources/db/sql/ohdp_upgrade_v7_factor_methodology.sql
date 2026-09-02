ALTER TABLE ohdp_factor_template
    ADD COLUMN detection_basis VARCHAR(1000) NULL COMMENT '检测依据(标准)' AFTER default_limit_unit,
    ADD COLUMN detection_method VARCHAR(500) NULL COMMENT '检测方法' AFTER detection_basis,
    ADD COLUMN detection_instruments VARCHAR(1000) NULL COMMENT '检测仪器及编号' AFTER detection_method;

UPDATE ohdp_factor_template SET
    detection_basis = '《工作场所物理因素测量 第8部分：噪声》（GBZ/T189.8-2007）',
    detection_method = '仪器直读法',
    detection_instruments = 'ASV5910个体声暴露计：A-777、A-778、A-779、A-780；BSWA309倍频程声级计：A-832'
WHERE deleted = 0 AND factor_name LIKE '%噪声%' AND (detection_basis IS NULL OR detection_basis = '');

UPDATE ohdp_factor_template SET
    detection_basis = '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气中粉尘测定 第1部分：总粉尘浓度》(GBZ/T 192.1-2007)',
    detection_method = '称重法',
    detection_instruments = '（1）FCC-25型防爆采样器：A-906（2）CPA225D电子天平：A-522'
WHERE deleted = 0 AND (factor_name LIKE '%总粉尘%' OR factor_name LIKE '%电焊烟尘%' OR factor_name LIKE '%金属粉尘%' OR factor_name LIKE '%其他粉尘%') AND (detection_basis IS NULL OR detection_basis = '');

UPDATE ohdp_factor_template SET
    detection_basis = '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气有毒物质测定 第17部分：锰及其化合物》(GBZ/T 300.17-2017)',
    detection_method = '火焰原子吸收光谱法',
    detection_instruments = '（1）FCC-3000G型防爆采样器：A-890（2）FCC-25型防爆采样器：A-907（3）ZEEnit700P原子吸收分光光度计：A-333'
WHERE deleted = 0 AND factor_name LIKE '%锰%' AND (detection_basis IS NULL OR detection_basis = '');

UPDATE ohdp_factor_template SET
    detection_basis = '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气有毒物质测定 第66部分 苯、甲苯、二甲苯和乙苯》（GBZ/T 300.66-2017）',
    detection_method = '溶剂解吸-气相色谱法',
    detection_instruments = '（1）FCC-1500D型防爆采样器：A-820、A-819（2）GC2030气相色谱仪：A-694'
WHERE deleted = 0 AND (factor_name LIKE '%苯%' OR factor_name LIKE '%甲苯%' OR factor_name LIKE '%二甲苯%' OR factor_name LIKE '%乙苯%') AND (detection_basis IS NULL OR detection_basis = '');

UPDATE ohdp_factor_template SET
    detection_basis = '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气有毒物质测定》系列标准',
    detection_method = '溶剂解吸-气相色谱法',
    detection_instruments = '（1）FCC-1500D型防爆采样器（2）GC2030气相色谱仪'
WHERE deleted = 0 AND (factor_name LIKE '%丙酮%' OR factor_name LIKE '%异丙醇%' OR factor_name LIKE '%乙醇%' OR factor_name LIKE '%丁酮%') AND (detection_basis IS NULL OR detection_basis = '');

UPDATE ohdp_factor_template SET
    detection_basis = '（1）《工作场所空气中有害物质监测的采样规范》（GBZ 159-2004）（2）《工作场所空气有毒物质测定 第18部分：铅及其化合物》(GBZ/T 300.18-2017)',
    detection_method = '火焰原子吸收光谱法',
    detection_instruments = '（1）FCC-3000G型防爆采样器（2）ZEEnit700P原子吸收分光光度计：A-333'
WHERE deleted = 0 AND factor_name LIKE '%铅%' AND (detection_basis IS NULL OR detection_basis = '');
