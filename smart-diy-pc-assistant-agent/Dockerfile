FROM python:3.12-slim
WORKDIR /app
ARG PIP_INDEX_URL=https://pypi.org/simple
COPY pyproject.toml README.md requirements.lock ./
COPY app ./app
COPY scripts ./scripts
RUN pip install --no-cache-dir --index-url "$PIP_INDEX_URL" -r requirements.lock && pip install --no-cache-dir --no-deps . && useradd -m agent && mkdir data && chown agent:agent data
USER agent
EXPOSE 8000
CMD ["uvicorn","app.main:app","--host","0.0.0.0","--port","8000"]
