import json
import sys
import time
import urllib.error
import urllib.request


BASE_URL = "http://127.0.0.1:8088"
ENV_FILE = "/opt/bhfyzx/shared/.env"


def load_env(path):
    values = {}
    with open(path, "r", encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key] = value
    return values


def request(method, path, payload=None, token=None):
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as response:
        body = response.read()
        return response.status, None if not body else json.loads(body.decode("utf-8"))


def main():
    env = load_env(ENV_FILE)
    status, login = request("POST", "/api/auth/login", {
        "username": env["ADMIN_USERNAME"],
        "password": env["ADMIN_INITIAL_PASSWORD"],
    })
    if status != 200 or not login.get("token"):
        raise RuntimeError("管理员 API 登录失败")
    token = login["token"]

    status, ct_catalog = request("GET", "/api/radiology/catalog/CT", token=token)
    ctdi = next(item for item in ct_catalog if item["code"] == "CTDI")
    ctdi_fields = {field["code"] for field in ctdi["fields"]}
    expected_ctdi_fields = {
        "CTDI-HEAD-MANUFACTURER", "CTDI-BODY-MANUFACTURER",
        "CTDI-HEAD-BASELINE", "CTDI-BODY-BASELINE",
    }
    if not expected_ctdi_fields.issubset(ctdi_fields):
        raise RuntimeError("线上 CTDI 厂家值/基线字段不完整")

    case_id = None
    actual = None
    try:
        code = f"ONLINE-SMOKE-{int(time.time())}"
        status, case = request("POST", "/api/radiology/cases", {
            "caseCode": code,
            "modality": "DR",
            "inspectedUnitName": "线上自动化验证（完成后删除）",
            "equipmentName": "DR 自动化验证设备",
        }, token)
        case_id = case["id"]

        status, points = request("PUT", f"/api/radiology/cases/{case_id}/protection-points", {
            "points": [
                {
                    "pointCode": "RPD-A",
                    "pointName": "超限后年剂量合格点",
                    "readings": [30000, 30000, 30000, 30000, 30000],
                    "annualWorkloadMas": 1000000,
                    "occupancyFactor": 1,
                    "useFactor": 1,
                    "sortNo": 1,
                },
                {
                    "pointCode": "RPD-B",
                    "pointName": "超限后年剂量不合格点",
                    "readings": [30000, 30000, 30000, 30000, 30000],
                    "annualWorkloadMas": 4000000,
                    "occupancyFactor": 1,
                    "useFactor": 1,
                    "sortNo": 2,
                },
            ]
        }, token)
        actual = [
            (str(point["resultValue"]), str(point["annualEffectiveDose"]), point["judgement"])
            for point in points
        ]
        expected = [
            ("30.0", "0.08333333", "QUALIFIED"),
            ("30.0", "0.33333333", "UNQUALIFIED"),
        ]
        if actual != expected:
            raise RuntimeError(f"DR 年剂量线上判定不符合预期：{actual}")
    finally:
        if case_id is not None:
            request("DELETE", f"/api/radiology/cases/{case_id}", token=token)

    status, remaining = request(
        "GET", "/api/radiology/cases?keyword=ONLINE-SMOKE-&page=0&size=20", token=token
    )
    if remaining["totalElements"] != 0:
        raise RuntimeError("线上验证案例未完成清理")
    print(json.dumps({
        "health": "ok",
        "login": "ok",
        "calculationVersion": case["calculationVersion"],
        "ctdiProfileFields": "ok",
        "annualDoseResults": actual,
        "testDataCleanup": "done",
    }, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except (KeyError, StopIteration, urllib.error.URLError, RuntimeError) as error:
        print(f"ONLINE_SMOKE_FAILED: {error}", file=sys.stderr)
        raise
