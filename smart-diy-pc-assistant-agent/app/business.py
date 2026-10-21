import httpx
from fastapi import HTTPException


class Business:
    def __init__(self, client: httpx.AsyncClient, token: str):
        self.client, self.token = client, token

    async def get(self, path, params=None):
        try:
            response = await self.client.get(path, headers={"Authorization": self.token}, params=params)
            if response.status_code in (401, 403):
                raise HTTPException(response.status_code, "登录已失效或没有访问权限")
            response.raise_for_status()
            body = response.json()
            if body.get("code") != 0 and body.get("code") != 200:
                raise HTTPException(body["code"] if body.get("code") in (401, 403) else 400, body.get("message", "业务查询失败"))
            return body["data"]
        except httpx.HTTPError as exc:
            raise HTTPException(503, "Java 业务服务不可用，请检查服务连接") from exc

    async def user(self):
        return await self.get("/api/auth/me")

    async def hardware(self):
        return await self.get("/api/hardware-parts")
