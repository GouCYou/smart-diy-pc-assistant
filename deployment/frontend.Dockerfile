FROM node:24-alpine AS build
ARG FRONTEND=smart-diy-pc-assistant-web
ARG PUBLIC_BASE=/
WORKDIR /workspace
COPY shared ./shared
COPY ${FRONTEND}/package*.json ./${FRONTEND}/
RUN cd ${FRONTEND} && npm ci
COPY ${FRONTEND}/ ./${FRONTEND}/
RUN cd ${FRONTEND} && VITE_PUBLIC_BASE=${PUBLIC_BASE} npm run build && cp -R dist /output
FROM nginx:alpine
COPY deployment/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /output /usr/share/nginx/html
