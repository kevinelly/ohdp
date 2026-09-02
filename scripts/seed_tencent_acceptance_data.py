import base64
import datetime as dt
import json
import os
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request


BASE_URL = os.environ.get("BHFYZX_BASE_URL", "http://127.0.0.1:8088").rstrip("/")
ENV_FILE = os.environ.get("BHFYZX_ENV_FILE", "/opt/bhfyzx/shared/.env")
RUN_TAG = "验收测试-20260827"
TODAY = dt.date.today().isoformat()


def load_env(path):
    values = {}
    with open(path, "r", encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key] = value
    return values


def api_request(method, path, payload=None, token=None, timeout=60):
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(BASE_URL + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read()
            return None if not raw else json.loads(raw.decode("utf-8"))
    except urllib.error.HTTPError as error:
        details = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} -> HTTP {error.code}: {details}") from error


def upload_file(path, file_name, media_type, content, token):
    boundary = "----BHFYZXAcceptanceBoundary"
    chunks = [
        f"--{boundary}\r\n".encode(),
        b'Content-Disposition: form-data; name="category"\r\n\r\n',
        b"POINT_MAP\r\n",
        f"--{boundary}\r\n".encode(),
        f'Content-Disposition: form-data; name="file"; filename="{file_name}"\r\n'.encode(),
        f"Content-Type: {media_type}\r\n\r\n".encode(),
        content,
        b"\r\n",
        f"--{boundary}--\r\n".encode(),
    ]
    request = urllib.request.Request(
        BASE_URL + path,
        data=b"".join(chunks),
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        details = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"POST {path} -> HTTP {error.code}: {details}") from error


def page_content(response):
    if response is None:
        return []
    if isinstance(response, list):
        return response
    return response.get("content", [])


def ensure_item(list_path, post_path, identity_field, identity_value, payload, token):
    items = page_content(api_request("GET", list_path, token=token))
    for item in items:
        if str(item.get(identity_field, "")) == str(identity_value):
            return item, False
    return api_request("POST", post_path, payload, token), True


def ensure_radiology_case(payload, token):
    query = urllib.parse.quote(payload["caseCode"])
    items = page_content(api_request("GET", f"/api/radiology/cases?keyword={query}&page=0&size=50", token=token))
    for item in items:
        if item.get("caseCode") == payload["caseCode"]:
            return api_request("GET", f"/api/radiology/cases/{item['id']}", token=token), False
    return api_request("POST", "/api/radiology/cases", payload, token), True


def measurement(item, field, value=None, text=None):
    result = {"itemCode": item, "fieldCode": field}
    if value is not None:
        result["valueNum"] = value
    if text is not None:
        result["valueText"] = text
    return result


def create_master_data(token, summary):
    created = []
    companies = [
        ("验收测试·北海市滨海医院", "北海市海城区北海大道168号", "陆敏", "0779-5551020", "医疗卫生"),
        ("验收测试·北海市海城区精密制造有限公司", "北海市海城区工业园区科盛路66号", "周海", "0779-3218096", "装备制造"),
        ("验收测试·北海市铁山港区新材料科技有限公司", "北海市铁山港区兴港镇产业园科创路9号", "何静", "0779-2683068", "新材料"),
    ]
    company_records = []
    for name, address, contact, phone, industry in companies:
        item, was_created = ensure_item(
            "/api/ohdp/maintenance/company-profiles?page=0&size=200",
            "/api/ohdp/maintenance/company-profiles",
            "companyName",
            name,
            {"companyName": name, "companyAddress": address, "contactName": contact, "contactPhone": phone,
             "industryType": industry, "notes": f"{RUN_TAG}：拟真主数据"},
            token,
        )
        company_records.append(item)
        if was_created:
            created.append(f"单位:{name}")

    position_templates = [
        {"jobName": "验收测试·焊接作业岗", "shiftSystem": "两班制", "workingHours": "8 h/d", "operationType": "手工电弧焊", "operationDuration": "5 h/d", "hazards": "电焊烟尘、锰及其无机化合物、噪声", "ppe": "焊接面罩、防尘口罩、防噪耳塞", "protectionFacilities": "移动式焊烟净化器、局部排风", "notes": RUN_TAG},
        {"jobName": "验收测试·喷涂作业岗", "shiftSystem": "白班", "workingHours": "8 h/d", "operationType": "密闭喷涂", "operationDuration": "4 h/d", "hazards": "苯、甲苯、二甲苯", "ppe": "防毒面具、丁腈手套", "protectionFacilities": "水帘喷漆房、机械排风", "notes": RUN_TAG},
        {"jobName": "验收测试·DR 操作岗", "shiftSystem": "轮班制", "workingHours": "8 h/d", "operationType": "数字摄影操作", "operationDuration": "6 h/d", "hazards": "X 射线", "ppe": "个人剂量计", "protectionFacilities": "铅屏蔽、门灯联锁", "notes": RUN_TAG},
    ]
    position_records = []
    for payload in position_templates:
        item, was_created = ensure_item(
            "/api/ohdp/maintenance/position-templates?page=0&size=200",
            "/api/ohdp/maintenance/position-templates",
            "jobName", payload["jobName"], payload, token)
        position_records.append(item)
        if was_created:
            created.append(f"岗位模板:{payload['jobName']}")

    factor_templates = [
        {"factorName": "验收测试·噪声", "metricType": "8h等效声级", "defaultUnit": "dB(A)", "defaultLimitValue": "85", "defaultLimitUnit": "dB(A)", "notes": f"{RUN_TAG}；GBZ 2.2—2007"},
        {"factorName": "验收测试·电焊烟尘", "metricType": "PC-TWA", "defaultUnit": "mg/m³", "defaultLimitValue": "4", "defaultLimitUnit": "mg/m³", "notes": f"{RUN_TAG}；GBZ 2.1—2019"},
        {"factorName": "验收测试·苯", "metricType": "PC-TWA", "defaultUnit": "mg/m³", "defaultLimitValue": "6", "defaultLimitUnit": "mg/m³", "notes": f"{RUN_TAG}；GBZ 2.1—2019"},
        {"factorName": "验收测试·X射线", "metricType": "周围剂量当量率", "defaultUnit": "μSv/h", "defaultLimitValue": "2.5", "defaultLimitUnit": "μSv/h", "notes": f"{RUN_TAG}；GBZ 130—2020"},
    ]
    factor_template_records = []
    for payload in factor_templates:
        item, was_created = ensure_item(
            "/api/ohdp/maintenance/factor-templates?page=0&size=200",
            "/api/ohdp/maintenance/factor-templates",
            "factorName", payload["factorName"], payload, token)
        factor_template_records.append(item)
        if was_created:
            created.append(f"因素模板:{payload['factorName']}")

    dictionaries = [
        {"dictType": "DETECT_TYPE", "itemCode": "ACCEPT-WORKPLACE", "itemName": "验收测试·工作场所定期检测", "sortNo": 81, "enabled": True},
        {"dictType": "DETECT_TYPE", "itemCode": "ACCEPT-RAD-QC", "itemName": "验收测试·放射诊疗设备质量控制", "sortNo": 82, "enabled": True},
        {"dictType": "SAMPLE_TYPE", "itemCode": "ACCEPT-AIR", "itemName": "验收测试·工作场所空气样品", "sortNo": 83, "enabled": True},
    ]
    dictionary_records = []
    for payload in dictionaries:
        item, was_created = ensure_item(
            "/api/ohdp/maintenance/dictionary-items?dictType="
            + urllib.parse.quote(payload["dictType"])
            + "&page=0&size=300",
            "/api/ohdp/maintenance/dictionary-items",
            "itemCode", payload["itemCode"], payload, token)
        dictionary_records.append(item)
        if was_created:
            created.append(f"字典:{payload['itemCode']}")

    staff = [
        {"staffName": "张晓岚（验收）", "staffTitle": "检测工程师", "certNo": "ACCEPT-JC-2026-001", "contactPhone": "13800001001", "notes": RUN_TAG},
        {"staffName": "李明远（验收）", "staffTitle": "报告编制人", "certNo": "ACCEPT-BZ-2026-002", "contactPhone": "13800001002", "notes": RUN_TAG},
        {"staffName": "陈嘉禾（验收）", "staffTitle": "技术审核人", "certNo": "ACCEPT-SH-2026-003", "contactPhone": "13800001003", "notes": RUN_TAG},
        {"staffName": "王若岚（验收）", "staffTitle": "授权签字人", "certNo": "ACCEPT-QZ-2026-004", "contactPhone": "13800001004", "notes": RUN_TAG},
    ]
    staff_records = []
    for payload in staff:
        item, was_created = ensure_item(
            "/api/ohdp/maintenance/staff-profiles?page=0&size=200",
            "/api/ohdp/maintenance/staff-profiles",
            "certNo", payload["certNo"], payload, token)
        staff_records.append(item)
        if was_created:
            created.append(f"人员:{payload['staffName']}")

    report_texts = [
        {"templateType": "TASK_ORIGIN", "templateName": "验收测试·年度职业病危害定期检测", "templateContent": "受用人单位委托，依据年度职业病危害因素检测计划，对作业场所危害因素进行检测与评价。", "enabled": True},
        {"templateType": "EVALUATION_BASIS", "templateName": "验收测试·职业接触限值评价依据", "templateContent": "检测结果按照 GBZ 2.1—2019、GBZ 2.2—2007 及项目适用标准进行评价。", "enabled": True},
    ]
    report_text_records = []
    for payload in report_texts:
        item, was_created = ensure_item(
            "/api/ohdp/maintenance/report-text-templates?templateType="
            + urllib.parse.quote(payload["templateType"])
            + "&page=0&size=200",
            "/api/ohdp/maintenance/report-text-templates",
            "templateName", payload["templateName"], payload, token)
        report_text_records.append(item)
        if was_created:
            created.append(f"报告文本:{payload['templateName']}")

    basic_items = [
        ("/api/ohdp/basic/laws?page=0&size=200", "/api/ohdp/basic/laws", "title", "[验收测试] 中华人民共和国职业病防治法", {"title": "[验收测试] 中华人民共和国职业病防治法", "docNo": "主席令第24号", "level": "NATIONAL", "publishDate": "2018-12-29", "effectiveDate": "2018-12-29", "publisher": "全国人民代表大会常务委员会", "isValid": True, "isOpen": True, "notes": f"{RUN_TAG}；仅用于界面验收"}),
        ("/api/ohdp/basic/laws?page=0&size=200", "/api/ohdp/basic/laws", "title", "[验收测试] 放射诊疗管理规定", {"title": "[验收测试] 放射诊疗管理规定", "docNo": "卫生部令第46号", "level": "DEPARTMENT", "publishDate": "2006-01-24", "effectiveDate": "2006-03-01", "publisher": "原卫生部", "isValid": True, "isOpen": True, "notes": f"{RUN_TAG}；仅用于界面验收"}),
        ("/api/ohdp/basic/standards?page=0&size=200", "/api/ohdp/basic/standards", "standardCode", "WS 76—2020", {"standardName": "[验收测试] WS 76—2020 医用常规X射线诊断设备质量控制检测规范", "standardCode": "WS 76—2020", "level": "INDUSTRY", "useScope": "DR 设备质量控制", "publishDate": "2020-10-26", "isValid": True, "isOpen": True, "notes": RUN_TAG}),
        ("/api/ohdp/basic/standards?page=0&size=200", "/api/ohdp/basic/standards", "standardCode", "WS 519—2019", {"standardName": "[验收测试] WS 519—2019 X射线计算机体层摄影装置质量控制检测规范", "standardCode": "WS 519—2019", "level": "INDUSTRY", "useScope": "CT 设备质量控制", "publishDate": "2019-01-25", "isValid": True, "isOpen": True, "notes": RUN_TAG}),
        ("/api/ohdp/basic/standards?page=0&size=200", "/api/ohdp/basic/standards", "standardCode", "GBZ 130—2020", {"standardName": "[验收测试] GBZ 130—2020 放射诊断放射防护要求", "standardCode": "GBZ 130—2020", "level": "NATIONAL", "useScope": "放射诊断场所防护", "publishDate": "2020-04-03", "isValid": True, "isOpen": True, "notes": RUN_TAG}),
    ]
    for list_path, post_path, field, value, payload in basic_items:
        _, was_created = ensure_item(list_path, post_path, field, value, payload, token)
        if was_created:
            created.append(value)

    city_payload = {
        "regionCode": "ACCEPT-4505", "regionName": "验收测试·北海市",
        "parentId": None, "level": 2, "sortNo": 81,
    }
    city, was_created = ensure_item(
        "/api/ohdp/basic/regions?page=0&size=200", "/api/ohdp/basic/regions",
        "regionCode", city_payload["regionCode"], city_payload, token)
    if was_created:
        created.append(f"地区:{city_payload['regionName']}")
    elif any(city.get(field) != city_payload.get(field) for field in ("regionName", "parentId", "level", "sortNo")):
        city = api_request("PUT", f"/api/ohdp/basic/regions/{city['id']}", city_payload, token)
        created.append(f"地区层级更新:{city_payload['regionName']}")

    region_children = [
        {"regionCode": "ACCEPT-450502", "regionName": "验收测试·海城区", "level": 3, "sortNo": 82},
        {"regionCode": "ACCEPT-450503", "regionName": "验收测试·银海区", "level": 3, "sortNo": 83},
        {"regionCode": "ACCEPT-450512", "regionName": "验收测试·铁山港区", "level": 3, "sortNo": 84},
        {"regionCode": "ACCEPT-450521", "regionName": "验收测试·合浦县", "level": 3, "sortNo": 85},
    ]
    for payload in region_children:
        payload["parentId"] = city["id"]
        item, child_created = ensure_item(
            "/api/ohdp/basic/regions?page=0&size=200", "/api/ohdp/basic/regions",
            "regionCode", payload["regionCode"], payload, token)
        if child_created:
            created.append(f"地区:{payload['regionName']}")
        elif any(item.get(field) != payload.get(field) for field in ("regionName", "parentId", "level", "sortNo")):
            api_request("PUT", f"/api/ohdp/basic/regions/{item['id']}", payload, token)
            created.append(f"地区层级更新:{payload['regionName']}")

    devices = [
        {"deviceName": "验收测试·X射线剂量仪", "deviceType": "LAB_INSTRUMENT", "deviceModel": "RaySafe X2", "deviceNo": "ACCEPT-EQ-001", "manufacturer": "Unfors RaySafe", "calibrationDate": "2026-05-18", "calibrationExpiry": "2027-05-17", "status": "NORMAL", "notes": RUN_TAG},
        {"deviceName": "验收测试·CT性能检测模体", "deviceType": "LAB_INSTRUMENT", "deviceModel": "Catphan 604", "deviceNo": "ACCEPT-EQ-002", "manufacturer": "The Phantom Laboratory", "calibrationDate": "2026-04-20", "calibrationExpiry": "2027-04-19", "status": "NORMAL", "notes": RUN_TAG},
        {"deviceName": "验收测试·个体噪声剂量计", "deviceType": "SAMPLER", "deviceModel": "ASV5910", "deviceNo": "ACCEPT-EQ-003", "manufacturer": "杭州爱华", "calibrationDate": "2026-03-12", "calibrationExpiry": "2027-03-11", "status": "NORMAL", "notes": RUN_TAG},
        {"deviceName": "验收测试·粉尘采样器", "deviceType": "SAMPLER", "deviceModel": "FC-30", "deviceNo": "ACCEPT-EQ-004", "manufacturer": "盐城天悦", "calibrationDate": "2026-02-16", "calibrationExpiry": "2027-02-15", "status": "NORMAL", "notes": RUN_TAG},
    ]
    device_records = []
    for payload in devices:
        item, was_created = ensure_item("/api/ohdp/devices?page=0&size=200", "/api/ohdp/devices", "deviceNo", payload["deviceNo"], payload, token)
        device_records.append(item)
        if was_created:
            created.append(f"设备:{payload['deviceName']}")

    hazard_specs = [
        ("验收测试·苯", "ACCEPT-BENZENE", "CHEMICAL", "71-43-2", "Benzene", "PC_TWA", 6, "mg/m³", "GBZ 2.1—2019"),
        ("验收测试·锰及其无机化合物", "ACCEPT-MN", "CHEMICAL", "7439-96-5", "Manganese", "PC_TWA", 0.15, "mg/m³", "GBZ 2.1—2019"),
        ("验收测试·电焊烟尘", "ACCEPT-WELD-DUST", "DUST", "", "Welding fume", "PC_TWA", 4, "mg/m³", "GBZ 2.1—2019"),
        ("验收测试·噪声", "ACCEPT-NOISE", "PHYSICAL", "", "Noise", "OEL", 85, "dB(A)", "GBZ 2.2—2007"),
        ("验收测试·甲苯", "ACCEPT-TOLUENE", "CHEMICAL", "108-88-3", "Toluene", "PC_TWA", 50, "mg/m³", "GBZ 2.1—2019"),
        ("验收测试·其他粉尘", "ACCEPT-OTHER-DUST", "DUST", "", "Other dust", "PC_TWA", 8, "mg/m³", "GBZ 2.1—2019"),
        ("验收测试·一氧化碳", "ACCEPT-CO", "CHEMICAL", "630-08-0", "Carbon monoxide", "PC_TWA", 20, "mg/m³", "GBZ 2.1—2019"),
        ("验收测试·氨", "ACCEPT-AMMONIA", "CHEMICAL", "7664-41-7", "Ammonia", "PC_TWA", 20, "mg/m³", "GBZ 2.1—2019"),
        ("验收测试·过氧化氢", "ACCEPT-H2O2", "CHEMICAL", "7722-84-1", "Hydrogen peroxide", "PC_TWA", 1.5, "mg/m³", "GBZ 2.1—2019"),
    ]
    hazard_records = []
    for index, spec in enumerate(hazard_specs, start=1):
        name, code, category, cas, english, limit_type, limit_value, unit, standard_code = spec
        item, was_created = ensure_item(
            "/api/ohdp/hazard-factors?page=0&size=300", "/api/ohdp/hazard-factors", "factorCode", code,
            {"factorName": name, "factorCode": code, "factorCategory": category, "casNo": cas, "englishName": english, "sortNo": 80 + index, "enabled": True}, token)
        hazard_records.append(item)
        limits = api_request("GET", f"/api/ohdp/hazard-factors/{item['id']}/limits", token=token)
        for limit in limits:
            if limit.get("notes") == RUN_TAG and limit.get("limitType") == limit_type and str(limit.get("standardCode")) != standard_code:
                api_request("DELETE", f"/api/ohdp/hazard-factor-limits/{limit['id']}", token=token)
        limits = api_request("GET", f"/api/ohdp/hazard-factors/{item['id']}/limits", token=token)
        if not any(limit.get("limitType") == limit_type and str(limit.get("standardCode")) == standard_code for limit in limits):
            api_request("POST", f"/api/ohdp/hazard-factors/{item['id']}/limits", {"limitType": limit_type, "limitValue": limit_value, "limitUnit": unit, "standardCode": standard_code, "notes": RUN_TAG}, token)
        if was_created:
            created.append(f"危害因素:{name}")

    sds_payloads = [
        {"factorId": hazard_records[0]["id"], "substanceName": "验收测试·苯安全技术说明书", "casNo": "71-43-2", "version": "2026-A", "notes": f"{RUN_TAG}；演示数据"},
        {"factorId": hazard_records[1]["id"], "substanceName": "验收测试·锰及其化合物安全技术说明书", "casNo": "7439-96-5", "version": "2026-A", "notes": f"{RUN_TAG}；演示数据"},
        {"factorId": hazard_records[2]["id"], "substanceName": "验收测试·电焊烟尘安全技术说明书", "casNo": "", "version": "2026-A", "notes": f"{RUN_TAG}；演示数据"},
        {"factorId": hazard_records[4]["id"], "substanceName": "验收测试·甲苯安全技术说明书", "casNo": "108-88-3", "version": "2026-A", "notes": f"{RUN_TAG}；演示元数据，正式SDS需由客户提供"},
        {"factorId": hazard_records[6]["id"], "substanceName": "验收测试·一氧化碳安全技术说明书", "casNo": "630-08-0", "version": "2026-A", "notes": f"{RUN_TAG}；演示元数据，正式SDS需由客户提供"},
        {"factorId": hazard_records[7]["id"], "substanceName": "验收测试·氨安全技术说明书", "casNo": "7664-41-7", "version": "2026-A", "notes": f"{RUN_TAG}；演示元数据，正式SDS需由客户提供"},
        {"factorId": hazard_records[8]["id"], "substanceName": "验收测试·过氧化氢安全技术说明书", "casNo": "7722-84-1", "version": "2026-A", "notes": f"{RUN_TAG}；演示元数据，正式SDS需由客户提供"},
    ]
    for payload in sds_payloads:
        _, was_created = ensure_item("/api/ohdp/sds?page=0&size=200", "/api/ohdp/sds", "substanceName", payload["substanceName"], payload, token)
        if was_created:
            created.append(f"SDS:{payload['substanceName']}")

    method_payloads = [
        {"methodName": "验收测试·活性炭管采样/气相色谱法", "methodCode": "ACCEPT-GC-FID", "methodAbbr": "GC-FID", "formula": "C=(A-a)/b×V1/(V0×DE)", "formulaCode": "LINEAR_DESORPTION", "standardCode": "GBZ/T 300.66—2017", "resultDecimalPlaces": 2, "calcDecimalPlaces": 6, "enabled": True},
        {"methodName": "验收测试·滤膜称量法", "methodCode": "ACCEPT-GRAV", "methodAbbr": "GRAV", "formula": "C=(m2-m1)/V0", "formulaCode": "GRAVIMETRIC", "standardCode": "GBZ/T 192.1—2007", "resultDecimalPlaces": 2, "calcDecimalPlaces": 6, "enabled": True},
        {"methodName": "验收测试·声级计直读法", "methodCode": "ACCEPT-NOISE", "methodAbbr": "NOISE", "formula": "LEX,8h=LAeq,T+10lg(T/8)", "formulaCode": "NOISE_NORMALIZE", "standardCode": "GBZ/T 189.8—2007", "resultDecimalPlaces": 1, "calcDecimalPlaces": 3, "enabled": True},
    ]
    method_records = []
    for payload in method_payloads:
        item, was_created = ensure_item("/api/ohdp/config/detection-methods?page=0&size=200", "/api/ohdp/config/detection-methods", "methodCode", payload["methodCode"], payload, token)
        method_records.append(item)
        if was_created:
            created.append(f"检测方法:{payload['methodName']}")

    method_by_code = {method["methodCode"]: method for method in method_records}
    factor_method_codes = {
        "ACCEPT-BENZENE": "ACCEPT-GC-FID",
        "ACCEPT-TOLUENE": "ACCEPT-GC-FID",
        "ACCEPT-WELD-DUST": "ACCEPT-GRAV",
        "ACCEPT-OTHER-DUST": "ACCEPT-GRAV",
        "ACCEPT-NOISE": "ACCEPT-NOISE",
    }
    for factor in hazard_records:
        expected_method_code = factor_method_codes.get(factor.get("factorCode"))
        linked = api_request("GET", f"/api/ohdp/hazard-factors/{factor['id']}/methods", token=token)
        for link in linked:
            linked_code = next((m["methodCode"] for m in method_records if m["id"] == link.get("methodId")), None)
            if linked_code and linked_code != expected_method_code:
                api_request("DELETE", f"/api/ohdp/hazard-factor-methods/{link['id']}", token=token)
        if not expected_method_code:
            continue
        method = method_by_code[expected_method_code]
        linked = api_request("GET", f"/api/ohdp/hazard-factors/{factor['id']}/methods", token=token)
        if not any(link.get("methodId") == method["id"] for link in linked):
            api_request("POST", f"/api/ohdp/hazard-factors/{factor['id']}/methods", {"methodId": method["id"], "isDefault": True}, token)

    detect_types = [
        {"typeName": "验收测试·工作场所定期检测", "typeCode": "ACCEPT-OCC", "typeAbbr": "OCC", "sortNo": 81, "enabled": True},
        {"typeName": "验收测试·DR设备质量控制", "typeCode": "ACCEPT-DR", "typeAbbr": "DRQC", "sortNo": 82, "enabled": True},
        {"typeName": "验收测试·CT设备质量控制", "typeCode": "ACCEPT-CT", "typeAbbr": "CTQC", "sortNo": 83, "enabled": True},
    ]
    for payload in detect_types:
        _, was_created = ensure_item("/api/ohdp/config/detect-types?page=0&size=200", "/api/ohdp/config/detect-types", "typeCode", payload["typeCode"], payload, token)
        if was_created:
            created.append(f"检测分类:{payload['typeName']}")

    org = api_request("GET", "/api/ohdp/config/org", token=token)
    if not org or not org.get("orgName") or "验收测试" in org.get("orgName", ""):
        org = api_request("PUT", "/api/ohdp/config/org", {
            "orgName": "验收测试·北海市安衡职业卫生技术服务中心", "orgCode": "91450500ACCEPT2026",
            "orgAddress": "北海市海城区西南大道28号", "contactName": "办公室", "contactPhone": "0779-5558899",
            "certNo": "ACCEPT-CMA-2026", "sampleNoPrefix": "YS", "sampleNoYearFormat": "yyyy",
            "controlledNoPrefix": "SK", "reportNoPrefix": "BG", "reportNoYearFormat": "yyyy"}, token)
        created.append("机构配置")
        org_action = "created-or-updated"
    else:
        org_action = "preserved-existing"

    system_items = [
        ("/api/system/departments?page=0&size=200", "/api/system/departments", "deptCode", "ACCEPT-RAD", {"deptName": "验收测试·放射检测部", "deptCode": "ACCEPT-RAD", "enabled": True}),
        ("/api/system/departments?page=0&size=200", "/api/system/departments", "deptCode", "ACCEPT-LAB", {"deptName": "验收测试·理化实验室", "deptCode": "ACCEPT-LAB", "enabled": True}),
        ("/api/system/posts?page=0&size=200", "/api/system/posts", "postCode", "ACCEPT-INSPECTOR", {"postName": "验收测试·检测工程师", "postCode": "ACCEPT-INSPECTOR", "enabled": True}),
        ("/api/system/posts?page=0&size=200", "/api/system/posts", "postCode", "ACCEPT-REVIEWER", {"postName": "验收测试·报告审核人", "postCode": "ACCEPT-REVIEWER", "enabled": True}),
        ("/api/system/notices?page=0&size=200", "/api/system/notices", "title", "验收测试数据使用说明", {"title": "验收测试数据使用说明", "content": "系统内带“验收测试”标识的数据用于本次功能验收，可在验收完成后统一清理。", "noticeType": "NOTICE", "publisher": "系统管理员", "enabled": True}),
        ("/api/system/configs?page=0&size=200", "/api/system/configs", "configKey", "acceptance.data.tag", {"configKey": "acceptance.data.tag", "configValue": RUN_TAG, "configName": "验收测试数据标识"}),
    ]
    for list_path, post_path, field, value, payload in system_items:
        _, was_created = ensure_item(list_path, post_path, field, value, payload, token)
        if was_created:
            created.append(value)

    acceptance_role, role_created = ensure_item(
        "/api/system/roles?page=0&size=200", "/api/system/roles", "roleCode", "ACCEPTANCE_REVIEWER",
        {"roleCode": "ACCEPTANCE_REVIEWER", "roleName": "验收测试·验收查看员",
         "remark": f"{RUN_TAG}；用于展示角色与授权关系", "status": "ACTIVE"}, token)
    if role_created:
        created.append("角色:ACCEPTANCE_REVIEWER")

    acceptance_permission, permission_created = ensure_item(
        "/api/system/permissions?page=0&size=500", "/api/system/permissions", "permCode", "acceptance:data:read",
        {"permCode": "acceptance:data:read", "permName": "验收测试·查看验收数据", "permType": "API",
         "apiMethod": "GET", "apiPath": "/api/ohdp/projects", "parentId": None, "sortNo": 980,
         "status": "ACTIVE"}, token)
    if permission_created:
        created.append("权限:acceptance:data:read")

    acceptance_menu, menu_created = ensure_item(
        "/api/system/menus/tree", "/api/system/menus", "permCode", "acceptance:overview",
        {"permCode": "acceptance:overview", "permName": "验收测试·数据总览", "parentId": None,
         "sortNo": 980, "status": "ACTIVE"}, token)
    if menu_created:
        created.append("菜单:acceptance:overview")

    acceptance_user, user_created = ensure_item(
        "/api/users?username=acceptance_viewer&page=0&size=50", "/api/users", "username", "acceptance_viewer",
        {"username": "acceptance_viewer", "password": secrets.token_urlsafe(24), "employeeId": None,
         "role": "USER", "status": "INACTIVE"}, token)
    if user_created:
        created.append("用户:acceptance_viewer（停用）")

    expected_permission_ids = {acceptance_permission["id"]}
    current_permission_ids = set(api_request(
        "GET", f"/api/system/roles/{acceptance_role['id']}/permissions", token=token) or [])
    if current_permission_ids != expected_permission_ids:
        api_request("POST", f"/api/system/roles/{acceptance_role['id']}/permissions",
                    {"ids": sorted(expected_permission_ids)}, token)

    expected_role_ids = {acceptance_role["id"]}
    current_role_ids = set(api_request(
        "GET", f"/api/system/users/{acceptance_user['id']}/roles", token=token) or [])
    if current_role_ids != expected_role_ids:
        api_request("POST", f"/api/system/users/{acceptance_user['id']}/roles",
                    {"ids": sorted(expected_role_ids)}, token)

    summary["masterData"] = {
        "createdCount": len(created), "created": created, "orgConfig": org_action,
        "companies": len(company_records), "positionTemplates": len(position_records),
        "factorTemplates": len(factor_template_records), "dictionaries": len(dictionary_records),
        "staffProfiles": len(staff_records), "reportTextTemplates": len(report_text_records),
        "laws": 2, "standards": 3, "regions": 1 + len(region_children), "devices": len(device_records),
        "hazardFactors": len(hazard_records), "hazardLimits": len(hazard_records), "sdsRecords": len(sds_payloads),
        "detectionMethods": len(method_records), "detectionTypes": len(detect_types),
        "systemDepartments": 2, "systemPosts": 2, "systemNotices": 1, "systemConfigs": 1,
        "systemUsers": 1, "systemRoles": 1, "systemMenus": 1, "systemPermissions": 1,
    }
    return {
        "companies": company_records, "positions": position_records, "factorTemplates": factor_template_records,
        "dictionaries": dictionary_records, "staff": staff_records, "reportTexts": report_text_records,
        "hazards": hazard_records, "methods": method_records,
    }


def create_ohdp_projects(token, master, summary):
    specs = [
        ("验收测试·北海市海城区精密制造有限公司", "机加工与焊接作业场所", [("焊接作业岗", "电焊烟尘", "PC-TWA", 2.6, 4.0), ("焊接作业岗", "锰及其无机化合物", "PC-TWA", 0.11, 0.15), ("机加工岗", "噪声", "8h等效声级", 83.2, 85.0)]),
        ("验收测试·北海市铁山港区新材料科技有限公司", "涂装与配料作业场所", [("喷涂作业岗", "苯", "PC-TWA", 4.2, 6.0), ("喷涂作业岗", "甲苯", "PC-TWA", 32.0, 50.0), ("配料作业岗", "噪声", "8h等效声级", 86.4, 85.0)]),
        ("验收测试·北海市港区机械设备维修基地", "打磨与检修作业场所", [("打磨作业岗", "其他粉尘", "PC-TWA", 5.1, 8.0), ("打磨作业岗", "噪声", "8h等效声级", 88.1, 85.0), ("检修作业岗", "一氧化碳", "PC-TWA", 12.5, 20.0)]),
        ("验收测试·北海市银海区冷链物流中心", "制冷机房与装卸区", [("制冷机房巡检岗", "氨", "PC-TWA", 12.0, 20.0), ("装卸作业岗", "噪声", "8h等效声级", 80.6, 85.0), ("清洗消毒岗", "过氧化氢", "PC-TWA", 0.9, 1.5)]),
    ]
    project_addresses = [
        "北海市海城区工业园区科盛路66号",
        "北海市铁山港区兴港镇产业园科创路9号",
        "北海市铁山港区营盘镇港区大道36号",
        "北海市银海区福成镇冷链物流园18号",
    ]
    existing = page_content(api_request("GET", "/api/ohdp/projects?page=0&size=500", token=token))
    project_summaries = []
    for index, (company, sample_name, result_specs) in enumerate(specs, start=1):
        marker = f"{RUN_TAG}-OHDP-{index:02d}"
        found = next((item for item in existing if marker in (item.get("notes") or "")), None)
        if found:
            project_summaries.append({"id": found["id"], "projectCode": found["projectCode"], "status": found["status"], "reused": True})
            continue
        staff = master["staff"]
        project = api_request("POST", "/api/ohdp/projects", {
            "companyName": company, "companyAddress": project_addresses[index - 1],
            "detectType": "工作场所职业病危害因素定期检测", "samplingDate": TODAY, "reportDate": TODAY,
            "sampleName": sample_name, "submitUnit": company, "entrustUnit": company,
            "taskOrigin": "受用人单位委托，对作业场所职业病危害因素进行年度定期检测。",
            "evaluationBasis": "GBZ 2.1—2019《工作场所有害因素职业接触限值 第1部分：化学有害因素》；GBZ 2.2—2007《工作场所有害因素职业接触限值 第2部分：物理因素》。",
            "writerStaffId": staff[1]["id"], "reviewerStaffId": staff[2]["id"], "approverStaffId": staff[3]["id"],
            "writerName": staff[1]["staffName"], "reviewerName": staff[2]["staffName"], "approverName": staff[3]["staffName"],
            "writerCertNo": staff[1]["certNo"], "reviewerCertNo": staff[2]["certNo"], "approverCertNo": staff[3]["certNo"],
            "statementDate": TODAY, "notes": f"{marker}；拟真验收项目",
        }, token)
        positions = []
        unique_jobs = []
        for job, *_ in result_specs:
            if job not in unique_jobs:
                unique_jobs.append(job)
        for job_index, job in enumerate(unique_jobs, start=1):
            positions.append(api_request("POST", f"/api/ohdp/projects/{project['id']}/positions", {
                "jobName": job, "shiftSystem": "白班" if job_index == 1 else "两班制", "workingHours": "8 h/d",
                "workerCount": 6 + index * 2 + job_index, "workLocation": f"{job}作业区", "operationType": job.replace("岗", ""),
                "operationContent": f"{job}日常生产、巡检与设备维护", "operationDuration": "6 h/d",
                "hazards": "、".join(spec[1] for spec in result_specs if spec[0] == job),
                "ppe": "防护口罩、防护手套、防噪耳塞", "protectionFacilities": "局部排风、隔声和警示标识",
            }, token))
        results = []
        for row, spec in enumerate(result_specs):
            job, factor, metric, value, limit = spec
            position = next(item for item in positions if item["jobName"] == job)
            unit = "dB(A)" if "声级" in metric else "mg/m³"
            results.append({"positionId": position["id"], "jobName": job, "factorName": factor,
                            "metricType": metric, "valueRaw": str(value), "valueNum": value,
                            "unit": unit, "limitValue": limit, "limitUnit": unit})
        api_request("POST", f"/api/ohdp/projects/{project['id']}/results/batch", {"items": results}, token)
        judgement = api_request("POST", f"/api/ohdp/projects/{project['id']}/judge", token=token)
        precheck = api_request("GET", f"/api/ohdp/projects/{project['id']}/report/precheck", token=token)
        report = None
        if precheck.get("passed"):
            report = api_request("POST", f"/api/ohdp/projects/{project['id']}/report/generate", token=token)
        project_summaries.append({
            "id": project["id"], "projectCode": project["projectCode"], "companyName": company,
            "positions": len(positions), "results": len(results), "qualified": judgement.get("qualifiedCount"),
            "unqualified": judgement.get("unqualifiedCount"), "precheckPassed": precheck.get("passed"),
            "reportFile": None if report is None else report.get("fileName"), "reused": False,
        })
    summary["ohdpProjects"] = project_summaries
    return project_summaries


def create_lab_data(token, projects, master, summary):
    tasks = []
    for index, project in enumerate(projects[:2], start=1):
        existing = page_content(api_request("GET", f"/api/ohdp/lab/tasks?projectId={project['id']}&page=0&size=50", token=token))
        task = next((item for item in existing if RUN_TAG in (item.get("notes") or "")), None)
        if task:
            tasks.append({"id": task["id"], "taskCode": task["taskCode"], "reused": True})
            continue
        sample_payloads = [
            {"sampleCode": f"YS-AIR-{index:02d}-01", "factorName": "苯", "sampleCount": 3, "sampleDate": TODAY, "sampleType": "空气样品", "notes": RUN_TAG},
            {"sampleCode": f"YS-DUST-{index:02d}-02", "factorName": "电焊烟尘", "sampleCount": 3, "sampleDate": TODAY, "sampleType": "滤膜样品", "notes": RUN_TAG},
        ]
        task = api_request("POST", "/api/ohdp/lab/tasks", {
            "projectId": project["id"], "issuerName": "张晓岚（验收）", "issuedDate": TODAY,
            "notes": f"{RUN_TAG}；实验室内部检测任务", "samples": sample_payloads,
        }, token)
        detail = api_request("GET", f"/api/ohdp/lab/tasks/{task['id']}", token=token)
        api_request("POST", f"/api/ohdp/lab/tasks/{task['id']}/receive", {"receiverName": "实验员赵青（验收）"}, token)
        result = api_request("POST", "/api/ohdp/lab/results", {
            "taskId": task["id"], "sampleId": detail["samples"][0]["id"], "factorId": master["hazards"][0]["id"],
            "factorName": "苯", "methodId": master["methods"][0]["id"], "methodName": master["methods"][0]["methodName"],
            "desorptionVolume": 1.0, "desorptionEfficiency": 0.96, "samplingVolume": 4.5,
            "resultDecimalPlaces": 2, "calcDecimalPlaces": 6, "resultUnit": "mg/m³",
            "minDetectValue": 0.1, "minQuantifyValue": 0.3, "analystName": "实验员赵青（验收）",
            "analysisDate": TODAY, "notes": RUN_TAG,
            "curvePoints": [
                {"pointIndex": 1, "concentration": 0.0, "absorbance": 0.006, "absorbanceRaw": 0.006, "isExcluded": False},
                {"pointIndex": 2, "concentration": 2.0, "absorbance": 0.118, "absorbanceRaw": 0.118, "isExcluded": False},
                {"pointIndex": 3, "concentration": 5.0, "absorbance": 0.291, "absorbanceRaw": 0.291, "isExcluded": False},
                {"pointIndex": 4, "concentration": 10.0, "absorbance": 0.579, "absorbanceRaw": 0.579, "isExcluded": False},
                {"pointIndex": 5, "concentration": 20.0, "absorbance": 1.151, "absorbanceRaw": 1.151, "isExcluded": False},
            ],
        }, token)
        calc = api_request("POST", f"/api/ohdp/lab/results/{result['id']}/calculate", token=token)
        sample_calc = api_request("POST", f"/api/ohdp/lab/results/{result['id']}/calc-sample", {"absorbance": 0.368 + index * 0.02, "samplingVolume": 4.5}, token)
        api_request("POST", f"/api/ohdp/lab/tasks/{task['id']}/submit", token=token)
        if index == 1:
            api_request("POST", f"/api/ohdp/lab/tasks/{task['id']}/review", {"approved": True, "reviewNote": "曲线相关性、样品信息和计算结果复核通过。"}, token)
        tasks.append({"id": task["id"], "taskCode": task["taskCode"], "samples": len(detail["samples"]),
                      "correlationR": calc.get("correlationR"), "resultValue": sample_calc.get("resultValue"), "reused": False})
    summary["labTasks"] = tasks


def dr_values(variant):
    base = [
        measurement("HVL", "HVL1", 2.20 + variant * 0.02), measurement("HVL", "HVL2", 2.30 + variant * 0.02), measurement("HVL", "HVL3", 2.40 + variant * 0.02),
        measurement("TVD", "TVD1-0", 60), measurement("TVD", "TVD1-1", 60.2), measurement("TVD", "TVD1-2", 59.9), measurement("TVD", "TVD1-3", 60.1),
        measurement("TVD", "TVD2-0", 80), measurement("TVD", "TVD2-1", 80.2), measurement("TVD", "TVD2-2", 79.9), measurement("TVD", "TVD2-3", 80.1),
        measurement("TVD", "TVD3-0", 100), measurement("TVD", "TVD3-1", 100.2), measurement("TVD", "TVD3-2", 99.9), measurement("TVD", "TVD3-3", 100.1),
        measurement("RPD", "k", 1.27), measurement("RPD", "a", 0.001),
    ]
    for index in range(1, 11):
        base.append(measurement("RPD", f"RPD0-{index}", 88 + index + variant))
    if variant == 2:
        base.extend([
            measurement("STP", "STP-K1", 1.199), measurement("STP", "STP-K2", 5.248), measurement("STP", "STP-K3", 10.42), measurement("STP", "STP-K4", 19.21), measurement("STP", "STP-K5", 33.41),
            measurement("STP", "STP-R1", 60918.117), measurement("STP", "STP-R2", 49839.441), measurement("STP", "STP-R3", 40281.706), measurement("STP", "STP-R4", 28378.819), measurement("STP", "STP-R5", 20179.48),
            measurement("LCR", "LCR1（可观察细节数）", 8), measurement("LCR", "LCR2（可观察对比度）", 0.5), measurement("LCR", "厂家规定值/基线值", 10),
        ])
    return base


def ct_values(variant):
    values = [
        measurement("HUA", "HUA1", -0.8 + variant * 0.1), measurement("HUA", "HUA2", 0.2), measurement("HUA", "HUA3", 0.6),
        measurement("LBA", "LBA", 1.2 + variant * 0.1), measurement("MTF", "MTF", 6.0 - variant * 0.1),
        measurement("NPS", "NPS1", 3.4 + variant * 0.1), measurement("NPS", "NPS2", 3.5 + variant * 0.1), measurement("NPS", "NPS3", 3.6 + variant * 0.1), measurement("NPS", "NPS-n", 10),
        measurement("RPD", "k", 1.27), measurement("RPD", "a", 0.001),
    ]
    for index in range(1, 11):
        values.append(measurement("RPD", f"RPD0-{index}", 82 + index + variant))
    if variant in (1, 2):
        values.extend([
            measurement("CTDI", "层数n1", 3), measurement("CTDI", "层厚T1", 10), measurement("CTDI", "刻度系数k1", 1),
            measurement("CTDI", "层数n2", 3), measurement("CTDI", "层厚T2", 10), measurement("CTDI", "刻度系数k2", 1),
            measurement("CTDI", "CTDI-HEAD-MANUFACTURER", 35), measurement("CTDI", "CTDI-BODY-MANUFACTURER", 18),
            measurement("CTDI", "CTDI-HEAD-BASELINE", 33.5), measurement("CTDI", "CTDI-BODY-BASELINE", 17.2),
        ])
        for prefix in ("HC", "HU", "HD", "HL", "HR", "SC", "SU", "SD", "SL", "SR"):
            for repeat in (1, 2, 3):
                values.append(measurement("CTDI", f"{prefix}D{repeat}", 100 + variant))
    if variant == 3:
        values.extend([
            measurement("LCD", "NUM1", 1), measurement("LCD", "mm1", 2.0),
            measurement("LCD", "NUM0.5", 1), measurement("LCD", "mm0.5", 2.4),
            measurement("LCD", "NUM0.3", 1), measurement("LCD", "mm0.3", 2.8),
        ])
    return values


def create_radiology_cases(token, summary):
    tiny_png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
    case_summaries = []
    units = ["北海市人民医院", "北海市第二人民医院", "北海市妇幼保健院", "北海市银海区人民医院"]
    unit_addresses = [
        "北海市海城区和平路83号",
        "北海市海城区文明路116号",
        "北海市海城区深圳路2号",
        "北海市银海区江苏路36号",
    ]
    for modality in ("DR", "CT"):
        for index in range(1, 5):
            code = f"YS-{modality}-20260827-V2-{index:02d}"
            payload = {
                "caseCode": code, "modality": modality, "sampleAcceptanceNo": f"SL-{modality}-2026-{index:03d}",
                "inspectedUnitName": f"验收测试·{units[index - 1]}", "inspectedUnitAddress": unit_addresses[index - 1],
                "contactName": ["陆敏", "黄凯", "周晴", "何俊"][index - 1], "contactPhone": f"13800002{index:03d}",
                "equipmentName": "数字化医用X射线摄影系统" if modality == "DR" else "X射线计算机体层摄影设备",
                "equipmentModel": f"{modality}-Clinical-{2020 + index}", "equipmentNo": f"EQ-{modality}-{index:03d}",
                "manufacturer": ["西门子医疗", "飞利浦医疗", "联影医疗", "GE医疗"][index - 1],
                "detectionCategory": ["状态检测", "验收检测", "稳定性检测", "状态检测"][index - 1],
                "detectionLocation": f"医学影像科 {modality}-{index} 室", "inspectionDate": TODAY, "reportDate": TODAY,
                "standardBasis": "WS 76—2020；GBZ 130—2020" if modality == "DR" else "WS 519—2019；GBZ 130—2020",
                "instrumentDescription": "RaySafe X2 X射线质量分析仪（校准有效期至2027-05-17）；标准模体与剂量探头",
                "writerName": "李明远（验收）", "reviewerName": "陈嘉禾（验收）", "approverName": "王若岚（验收）",
                "notes": f"{RUN_TAG}；拟真{modality}质量控制案例",
            }
            if modality == "CT":
                payload.update({"phantomName": "CT性能检测模体", "phantomManufacturer": "The Phantom Laboratory",
                                "phantomModel": "Catphan 604", "phantomSerialNo": f"CP604-AC-{index:03d}",
                                "phantomManualReference": "Catphan 604 使用说明书 Rev.2025；检测前完成中心校准"})
            case, created = ensure_radiology_case(payload, token)
            if not created:
                case_summaries.append({"id": case["id"], "caseCode": code, "modality": modality, "status": case["status"], "reused": True})
                continue
            values = dr_values(index) if modality == "DR" else ct_values(index)
            api_request("PUT", f"/api/radiology/cases/{case['id']}/measurements", {"values": values}, token)
            points = [
                {"pointCode": "P01", "pointName": "控制室操作位", "locationDescription": "观察窗前30 cm", "readings": [88 + index, 91 + index, 89 + index, 90 + index, 92 + index], "annualWorkloadMas": 900000, "occupancyFactor": 1, "useFactor": 1, "sortNo": 1},
                {"pointCode": "P02", "pointName": "机房防护门外", "locationDescription": "门缝外5 cm", "readings": [102 + index, 105 + index, 101 + index, 104 + index, 103 + index], "annualWorkloadMas": 900000, "occupancyFactor": 0.25, "useFactor": 1, "sortNo": 2},
                {"pointCode": "P03", "pointName": "相邻走廊", "locationDescription": "东墙外30 cm", "readings": [76 + index, 79 + index, 78 + index, 77 + index, 80 + index], "annualWorkloadMas": 900000, "occupancyFactor": 0.125, "useFactor": 1, "sortNo": 3},
            ]
            saved_points = api_request("PUT", f"/api/radiology/cases/{case['id']}/protection-points", {"points": points}, token)
            calculation = api_request("POST", f"/api/radiology/cases/{case['id']}/calculate", token=token)
            if index == 1:
                upload_file(f"/api/radiology/cases/{case['id']}/attachments", f"{code}-点位示意图.png", "image/png", tiny_png, token)
                api_request("POST", f"/api/radiology/cases/{case['id']}/workflow/submit", {"operatorName": "张晓岚（验收）", "comment": "原始记录与仪器读数核对完成"}, token)
                api_request("POST", f"/api/radiology/cases/{case['id']}/workflow/review", {"operatorName": "陈嘉禾（验收）", "comment": "计算结果与标准依据复核通过"}, token)
                api_request("POST", f"/api/radiology/cases/{case['id']}/workflow/approve", {"operatorName": "王若岚（验收）", "comment": "批准签发"}, token)
                report = api_request("POST", f"/api/radiology/cases/{case['id']}/reports", token=token)
            elif index == 2:
                api_request("POST", f"/api/radiology/cases/{case['id']}/workflow/submit", {"operatorName": "张晓岚（验收）", "comment": "提交技术审核"}, token)
                api_request("POST", f"/api/radiology/cases/{case['id']}/workflow/review", {"operatorName": "陈嘉禾（验收）", "comment": "审核通过"}, token)
                api_request("POST", f"/api/radiology/cases/{case['id']}/workflow/approve", {"operatorName": "王若岚（验收）", "comment": "批准待生成报告"}, token)
                report = None
            elif index == 3:
                api_request("POST", f"/api/radiology/cases/{case['id']}/workflow/submit", {"operatorName": "张晓岚（验收）", "comment": "提交审核，演示待审核状态"}, token)
                report = None
            else:
                report = None
            detail = api_request("GET", f"/api/radiology/cases/{case['id']}", token=token)
            case_summaries.append({
                "id": case["id"], "caseCode": code, "modality": modality, "status": detail["status"],
                "measurements": len(detail.get("measurements", [])), "protectionPoints": len(saved_points),
                "completedItems": calculation.get("completedItems"), "incompleteItems": calculation.get("incompleteItems"),
                "reportFile": None if report is None else report.get("fileName"), "reused": False,
            })
    summary["radiologyCases"] = case_summaries
    return case_summaries


def refresh_reused_reports(token, projects, radiology_cases, summary):
    refreshed = {"ohdp": [], "radiology": []}
    for project in projects:
        if project.get("reused") and project.get("status") == "REPORT_READY":
            report = api_request("POST", f"/api/ohdp/projects/{project['id']}/report/generate", token=token)
            refreshed["ohdp"].append(report.get("fileName"))
    for case in radiology_cases:
        if case.get("reused") and case.get("status") == "REPORTED":
            report = api_request("POST", f"/api/radiology/cases/{case['id']}/reports", token=token)
            refreshed["radiology"].append(report.get("fileName"))
    summary["refreshedReports"] = refreshed


def verify(token, summary):
    projects = page_content(api_request("GET", "/api/ohdp/projects?page=0&size=500", token=token))
    dr_cases = page_content(api_request("GET", "/api/radiology/cases?modality=DR&page=0&size=200", token=token))
    ct_cases = page_content(api_request("GET", "/api/radiology/cases?modality=CT&page=0&size=200", token=token))
    acceptance_projects = [item for item in projects if RUN_TAG in (item.get("notes") or "")]
    acceptance_dr = [item for item in dr_cases if (item.get("caseCode") or "").startswith("YS-DR-20260827-")]
    acceptance_ct = [item for item in ct_cases if (item.get("caseCode") or "").startswith("YS-CT-20260827-")]
    if len(acceptance_projects) < 4 or len(acceptance_dr) < 4 or len(acceptance_ct) < 4:
        raise RuntimeError("验收项目数量不足：职业危害、DR、CT 应分别不少于4条")
    summary["verification"] = {
        "acceptanceOhdpProjects": len(acceptance_projects), "acceptanceDrCases": len(acceptance_dr),
        "acceptanceCtCases": len(acceptance_ct), "totalAcceptanceCases": len(acceptance_projects) + len(acceptance_dr) + len(acceptance_ct),
        "radiologyStatuses": {status: sum(1 for item in acceptance_dr + acceptance_ct if item.get("status") == status)
                              for status in ("CALCULATED", "PENDING_REVIEW", "APPROVED", "REPORTED")},
    }


def main():
    env = load_env(ENV_FILE)
    login = api_request("POST", "/api/auth/login", {
        "username": env["ADMIN_USERNAME"], "password": env["ADMIN_INITIAL_PASSWORD"], "clientType": "acceptance-seed"
    })
    token = login["token"]
    summary = {"runTag": RUN_TAG, "generatedAt": dt.datetime.now().isoformat(timespec="seconds"), "baseUrl": BASE_URL}
    try:
        master = create_master_data(token, summary)
        projects = create_ohdp_projects(token, master, summary)
        create_lab_data(token, projects, master, summary)
        radiology_cases = create_radiology_cases(token, summary)
        refresh_reused_reports(token, projects, radiology_cases, summary)
        verify(token, summary)
    finally:
        try:
            api_request("POST", "/api/auth/logout", token=token)
        except Exception:
            pass
    output_path = os.environ.get("BHFYZX_SEED_SUMMARY", "/opt/bhfyzx/acceptance-seed-summary.json")
    with open(output_path, "w", encoding="utf-8") as handle:
        json.dump(summary, handle, ensure_ascii=False, indent=2)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"ACCEPTANCE_SEED_FAILED: {error}", file=sys.stderr)
        raise
