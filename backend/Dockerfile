FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt \
    && useradd --uid 10001 --create-home appuser

COPY --chown=appuser:appuser social_api/ ./social_api/
USER appuser
EXPOSE 8080

CMD ["sh", "-c", "exec python -m uvicorn social_api.main:app --host 0.0.0.0 --port ${PORT:-8080}"]
