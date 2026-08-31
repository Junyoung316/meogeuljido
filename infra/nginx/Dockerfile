# infra/nginx/Dockerfile
FROM nginx:1.27-alpine

RUN rm -f /etc/nginx/conf.d/default.conf
COPY conf.d/ /etc/nginx/conf.d/
RUN mkdir -p /usr/share/nginx/html
