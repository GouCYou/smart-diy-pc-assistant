export function createApi(tokenKey: string) {
  const origin = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");
  const url = (path: string) => origin + path;
  const headers = () => ({
    Authorization: `Bearer ${localStorage.getItem(tokenKey) || ""}`,
  });
  async function request<T = any>(
    path: string,
    method = "GET",
    body?: unknown,
  ): Promise<T> {
    let response: Response;
    try {
      response = await fetch(url(path), {
        method,
        headers: {
          ...headers(),
          ...(body instanceof FormData
            ? {}
            : { "Content-Type": "application/json" }),
        },
        body:
          body === undefined
            ? undefined
            : body instanceof FormData
              ? body
              : JSON.stringify(body),
      });
    } catch {
      throw new Error("服务连接失败，请检查网络或服务是否启动");
    }
    const data = await response.json().catch(() => ({}));
    if (!response.ok || (path.startsWith("/api") && data.code !== 0)) {
      if (response.status === 401 || data.code === 401)
        window.dispatchEvent(
          new CustomEvent("session-expired", { detail: tokenKey }),
        );
      const detail = Array.isArray(data.detail)
        ? data.detail
            .map((item: { msg?: string }) => item.msg)
            .filter(Boolean)
            .join("；")
        : data.detail;
      throw new Error(
        typeof detail === "string"
          ? detail
          : data.message || `请求失败 (${response.status})`,
      );
    }
    return path.startsWith("/api") ? data.data : data;
  }
  async function upload(file: File, usage = "community") {
    const form = new FormData();
    form.append("file", file);
    form.append("usage", usage);
    return request<{ key: string; url: string }>(
      "/api/files/upload",
      "POST",
      form,
    );
  }
  async function stream(
    path: string,
    onEvent: (type: string, data: any, id: number) => void,
    signal?: AbortSignal,
  ) {
    const response = await fetch(url(path), { headers: headers(), signal });
    if (!response.ok || !response.body)
      throw new Error("任务进度连接失败，请刷新重试");
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    try {
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder
          .decode(value, { stream: true })
          .replace(/\r\n/g, "\n");
        let end: number;
        while ((end = buffer.indexOf("\n\n")) >= 0) {
          const frame = buffer.slice(0, end);
          buffer = buffer.slice(end + 2);
          const lines = frame.split("\n");
          const type = lines
            .find((l) => l.startsWith("event:"))
            ?.slice(6)
            .trim();
          const data = lines
            .filter((l) => l.startsWith("data:"))
            .map((l) => l.slice(5).trimStart())
            .join("\n");
          if (type && data)
            onEvent(
              type,
              JSON.parse(data),
              Number(
                lines
                  .find((l) => l.startsWith("id:"))
                  ?.slice(3)
                  .trim() || 0,
              ),
            );
        }
      }
    } finally {
      reader.releaseLock();
    }
  }
  return { request, upload, stream, url };
}
