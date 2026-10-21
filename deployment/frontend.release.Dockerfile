FROM nginx:alpine
ARG FRONTEND=web
COPY ${FRONTEND}/ /usr/share/nginx/html/
