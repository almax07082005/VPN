# syntax=docker/dockerfile:1.7
# vpn-exit: egress hop deployed abroad.
# Build context is the repo root so we can COPY from docker/.
#   docker build -f exit/Dockerfile -t vpn-exit:dev ..

FROM ghcr.io/sagernet/sing-box:v1.10.3

RUN apk add --no-cache \
      bash \
      ca-certificates \
      coreutils \
      curl \
      gettext \
      jq \
      openssl \
      util-linux-misc

COPY docker/common.sh      /usr/local/lib/vpn/common.sh
COPY docker/vpn-supervisor /usr/local/bin/vpn-supervisor
COPY exit/entrypoint.sh    /usr/local/bin/entrypoint
COPY exit/config.json.tmpl /etc/vpn/config.json.tmpl

RUN chmod 755 /usr/local/bin/vpn-supervisor \
              /usr/local/bin/entrypoint

EXPOSE 443/tcp

ENTRYPOINT ["/usr/local/bin/entrypoint"]
CMD []
