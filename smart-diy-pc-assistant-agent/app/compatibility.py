"""Deterministic validation shared by tools and tests. Missing data never means PASS."""
from collections import Counter
import re


def check(parts: list[dict], budget: int | None = None) -> dict:
    by_type = {p["type"]: p for p in parts}
    errors, warnings = [], []
    for kind, count in Counter(p["type"] for p in parts).items():
        if count > 1:
            errors.append(f"同一配置不支持重复的 {kind}，请在单个型号规格中表达容量和数量")
    for kind in ("CPU", "MOTHERBOARD", "MEMORY", "STORAGE", "PSU", "CASE", "COOLER"):
        if kind not in by_type:
            warnings.append(f"缺少 {kind}，配置不完整")

    cpu, board, memory, psu, case, gpu, cooler = [by_type.get(k, {}) for k in ("CPU", "MOTHERBOARD", "MEMORY", "PSU", "CASE", "GPU", "COOLER")]
    for left, right, field, label in ((cpu, board, "socketType", "CPU 插槽"), (memory, board, "memoryType", "内存类型")):
        if left and right:
            if not left.get(field) or not right.get(field):
                warnings.append(f"{label}数据缺失，未验证")
            elif left[field].upper() != right[field].upper():
                errors.append(f"{label}不匹配：{left[field]} / {right[field]}")
    if board and case:
        formats = re.split(r"[,，;/、\s]+", (case.get("supportedFormFactors") or "").upper())
        if not board.get("formFactor") or not any(formats):
            warnings.append("机箱支持版型数据缺失，未验证")
        elif board["formFactor"].upper() not in formats:
            errors.append("主板版型不在机箱支持范围内")
    estimated = sum(int(p.get("wattage") or 0) for p in parts if p["type"] != "PSU") + 120
    if psu:
        if not psu.get("wattage") or not cpu.get("wattage") or (gpu and not gpu.get("wattage")):
            warnings.append("功耗参数不完整，电源余量未充分验证")
        if 0 < (psu.get("wattage") or 0) < estimated:
            errors.append(f"电源功率不足，按当前参数至少需要 {estimated}W")
    if gpu and case:
        if gpu.get("gpuLengthMm") and case.get("gpuLengthMm"):
            if gpu["gpuLengthMm"] > case["gpuLengthMm"]:
                errors.append("显卡长度超过机箱限长")
        else:
            warnings.append("显卡长度或机箱限长缺失，未验证")
    if cooler and case:
        if cooler.get("coolerHeightMm") and case.get("coolerHeightMm"):
            if cooler["coolerHeightMm"] > case["coolerHeightMm"]:
                errors.append("散热器高度超过机箱限高")
        else:
            warnings.append("散热器尺寸或冷排安装位置需人工核实")
    warnings.append("BIOS 版本、供电接头和 QVL 需结合厂商资料核实")
    total = sum(int(p.get("selectedPrice") or 0) for p in parts)
    unpriced = [p["model"] for p in parts if not p.get("selectedPrice")]
    if unpriced:
        warnings.append("部分部件没有有效商品报价，总价仅包含已报价部件")
    if budget and total > budget:
        errors.append(f"已报价金额超出预算 {total - budget} 元")
    return {"status": "BLOCK" if errors else "WARN" if warnings else "PASS", "errors": errors, "warnings": warnings, "quotedTotal": total, "unpricedParts": unpriced, "estimatedWattage": estimated}
