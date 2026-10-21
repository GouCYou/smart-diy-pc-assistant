import asyncio
import ipaddress
import re
import json
import socket
import time
import math
from urllib.parse import urlparse
import httpx
from bs4 import BeautifulSoup
from .store import now


async def public_url(url: str):
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.port not in (None, 443):
        raise ValueError("仅允许公开 HTTPS 网址")
    addresses = await asyncio.to_thread(socket.getaddrinfo, parsed.hostname, 443)
    if not addresses or any(not ipaddress.ip_address(item[4][0]).is_global for item in addresses):
        raise ValueError("不能访问内部网络地址")


class Crawler:
    def __init__(self):
        self.client = httpx.AsyncClient(timeout=20, follow_redirects=False, trust_env=False, headers={"User-Agent": "Mozilla/5.0 SmartDIY/1.0"})
        self.semaphore = asyncio.Semaphore(2)
        self.cache = {}

    async def close(self):
        await self.client.aclose()

    async def fetch_document(self, url):
        # Revalidate each redirect; never fetch arbitrary URLs through the Agent tool.
        for _ in range(4):
            await public_url(url)
            async with self.client.stream("GET", url) as response:
                if response.is_redirect:
                    from urllib.parse import urljoin
                    url = urljoin(url, response.headers["location"])
                    continue
                response.raise_for_status()
                if "text/html" not in response.headers.get("content-type", ""):
                    raise ValueError("网址导入仅支持 HTML，请使用文件上传导入其他资料")
                raw = bytearray()
                async for chunk in response.aiter_bytes():
                    raw.extend(chunk)
                    if len(raw) > 3_000_000:
                        raise ValueError("页面超过 3MB")
            soup = BeautifulSoup(raw, "html.parser")
            title = soup.title.get_text(" ", strip=True) if soup.title else url
            for element in soup(["script", "style", "nav", "footer", "header"]):
                element.decompose()
            return title, (soup.find("main") or soup.find("article") or soup).get_text("\n", strip=True)
        raise ValueError("网址重定向过多")

    @staticmethod
    def matches(title, keyword, part_type=""):
        normalized = title.lower()
        for token in re.findall(r"[a-z]*\d[a-z\d-]*", keyword.lower()):
            if token.isdigit() and len(token) == 1:
                continue  # Ryzen 5 / Core Ultra 7 are families, not exact model IDs.
            pieces = re.findall(r"[a-z]+|\d+", token)
            pattern = r"[\s-]*".join(re.escape(piece) for piece in pieces)
            if not re.search(r"(?<!\d)" + pattern + r"(?![a-z0-9])", normalized):
                return False
        for suffix in ("ti", "super", "xt", "xtx", "plus"):
            query_has = bool(re.search(r"(?<![a-z])" + suffix + r"(?![a-z])", keyword.lower()))
            title_has = bool(re.search(r"\d\s*" + suffix + r"(?![a-z])", normalized))
            if title_has and not query_has:
                return False
        forbidden = ["定金", "订金", "租赁", "维修", "空盒", "回收", "笔记本", "整机", "准系统", "主机", "组装机", "组装电脑", "全家桶"]
        forbidden += {"CPU": ["主板", "套装", "散热", "板U"], "MOTHERBOARD": ["套装", "板U"], "GPU": ["支架", "延长线", "水冷头"], "PSU": ["电源线", "适配器", "充电器"], "CASE": ["机箱风扇"], "COOLER": ["风扇套装"]}.get(part_type, [])
        return not any(word in title for word in forbidden)

    @staticmethod
    def parse_products(html, keyword, part_type=""):
        soup = BeautifulSoup(html, "html.parser")
        result, seen = [], set()
        for item in soup.select("li.gl-item[data-sku]"):
            sku = item.get("data-sku", "")
            title_node = item.select_one(".p-name em, .p-name")
            price_node = item.select_one(".p-price i")
            if not sku.isdigit() or sku in seen or not title_node:
                continue
            title = title_node.get_text(" ", strip=True)
            if not Crawler.matches(title, keyword, part_type):
                continue
            price_text = price_node.get_text(strip=True) if price_node else ""
            try:
                price = float(price_text.replace(",", ""))
                price = round(price, 2) if math.isfinite(price) else None
            except ValueError:
                price = None
            if price is not None and (not math.isfinite(price) or price <= 0):
                price = None
            image = item.select_one(".p-img img")
            image_url = (image.get("data-lazy-img") or image.get("src") or "") if image else ""
            if image_url.startswith("//"):
                image_url = "https:" + image_url
            shop = item.select_one(".p-shop")
            result.append({"skuId": sku, "title": title, "price": price, "image": image_url, "link": f"https://item.jd.com/{sku}.html", "shopName": shop.get_text(strip=True) if shop else "", "capturedAt": now(), "source": "jd", "keyword": keyword, "availability": "unknown", "priceType": "page"})
            seen.add(sku)
        return result[:12]

    @staticmethod
    def parse_hot_products(html, keyword, part_type=""):
        products = []
        for script in BeautifulSoup(html, "html.parser").find_all("script"):
            match = re.search(r"(?:var|let|const)\s+configs\s*=\s*", script.get_text())
            if not match:
                continue
            try:
                config, _ = json.JSONDecoder().raw_decode(script.get_text()[match.end():])
            except (ValueError, TypeError):
                continue
            if not isinstance(config, dict) or not isinstance(config.get("moduleInfo", []), list):
                continue
            for module in config.get("moduleInfo", []):
                if not isinstance(module, dict):
                    continue
                if not re.fullmatch(r"goods-pc-(?:filter|search)(?:-upgrade)?", str(module.get("id", ""))):
                    continue
                items = module.get("data", [])
                if not isinstance(items, list):
                    continue
                for item in items:
                    if not isinstance(item, dict):
                        continue
                    category = str(item.get("cid3", ""))
                    # JD distinguishes bare processors/GPUs from whole PCs and CPU-board bundles.
                    expected_category = {"CPU": "678", "GPU": "679"}.get(part_type)
                    if category and (category in {"11762", "17466"} or (expected_category and category != expected_category)):
                        continue
                    sku, title = str(item.get("id", "")), item.get("title", "")
                    if not isinstance(title, str):
                        continue
                    if not sku.isdigit() or not Crawler.matches(title, keyword, part_type):
                        continue
                    price = None
                    for key in ("jdPrice", "price", "pPrice", "salePrice"):
                        try:
                            raw_price = item.get(key, "")
                            if isinstance(raw_price, bool):
                                continue
                            candidate = float(raw_price)
                            if math.isfinite(candidate) and candidate > 0:
                                price = round(candidate, 2)
                                break
                        except (TypeError, ValueError):
                            pass
                    image = item.get("imageUrl") or ""
                    if not isinstance(image, str):
                        image = ""
                    if image.startswith("//"):
                        image = "https:" + image
                    elif image.startswith("jfs/"):
                        image = "https://img10.360buyimg.com/n1/" + image
                    elif not image.startswith("https://"):
                        image = ""
                    products.append({"skuId": sku, "title": title, "price": price, "image": image, "link": f"https://item.jd.com/{sku}.html", "shopName": item.get("shopName", ""), "capturedAt": now(), "source": "jd", "keyword": keyword, "availability": "unknown", "priceType": "page"})
        return list({p["skuId"]: p for p in products}.values())[:12]

    async def search(self, keyword, part_type=""):
        keyword = keyword.strip()[:120]
        if not keyword:
            return {"products": [], "warning": "请提供硬件型号"}
        cache_key = (keyword, part_type)
        cached = self.cache.get(cache_key)
        if cached and cached[0] > time.monotonic():
            return {**cached[1], "cached": True}
        async with self.semaphore:
            try:
                products, diagnostics = [], []
                for endpoint in ("https://re.jd.com/search", "https://search.jd.com/Search"):
                    try:
                        response = await self.client.get(endpoint, params={"keyword": keyword, "enc": "utf-8"})
                        if response.is_redirect:
                            diagnostics.append({"endpoint": endpoint, "status": "blocked", "httpStatus": response.status_code})
                            continue
                        response.raise_for_status()
                        products = self.parse_hot_products(response.text, keyword, part_type) if "re.jd.com" in endpoint else self.parse_products(response.text, keyword, part_type)
                        diagnostics.append({"endpoint": endpoint, "status": "ok" if products else "empty", "httpStatus": response.status_code})
                        if products:
                            break
                    except httpx.HTTPError as exc:
                        diagnostics.append({"endpoint": endpoint, "status": "error", "reason": type(exc).__name__})
                        continue
                data = {"products": products, "warning": "" if products else "商品页面未返回有效结果，可能需要登录或遭遇反爬；不能据此判断无货", "cached": False, "diagnostics": diagnostics}
            except httpx.HTTPError:
                data = {"products": [], "warning": "商品查询暂不可用，请稍后重试；未生成替代价格", "cached": False}
            if len(self.cache) > 500:
                self.cache.clear()
            self.cache[cache_key] = (time.monotonic() + (600 if data["products"] else 30), data)
            return data
