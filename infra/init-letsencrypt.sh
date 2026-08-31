# 최초 배포 1회만 실행. 이후에는 docker-compose.yml의 certbot 서비스가 갱신을 담당한다 (infra-design.md §4).
set -euo pipefail

DOMAIN="meogeuljido.example.com"
DATA_PATH="./infra/certbot-init"

# 1. nginx가 시작할 수 있도록 더미 인증서를 먼저 생성한다.
mkdir -p "$DATA_PATH/conf/live/$DOMAIN"
docker compose run --rm --entrypoint "\
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout '/etc/letsencrypt/live/$DOMAIN/privkey.pem' \
    -out '/etc/letsencrypt/live/$DOMAIN/fullchain.pem' \
    -subj '/CN=localhost'" certbot

# 2. 더미 인증서로 nginx를 기동한다.
docker compose up -d nginx

# 3. 더미 인증서를 지우고 webroot 방식으로 실제 인증서를 발급받는다.
docker compose run --rm --entrypoint "\
  rm -rf /etc/letsencrypt/live/$DOMAIN /etc/letsencrypt/archive/$DOMAIN /etc/letsencrypt/renewal/$DOMAIN.conf" certbot
docker compose run --rm certbot certonly --webroot -w /var/www/certbot -d "$DOMAIN"

# 4. 실제 인증서를 nginx가 다시 읽도록 reload한다.
docker compose exec nginx nginx -s reload
