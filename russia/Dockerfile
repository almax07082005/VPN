# syntax=docker/dockerfile:1.7
# vpn-russia: entry hop for clients inside Russia.
# Build context is the repo root so we can COPY from docker/.
#   docker build -f russia/Dockerfile -t vpn-russia:dev ..

FROM ghcr.io/sagernet/sing-box:v1.10.3

RUN apk add --no-cache \
      bash \
      ca-certificates \
      coreutils \
      curl \
      gettext \
      jq \
      libqrencode-tools \
      openssl \
      util-linux-misc

COPY docker/common.sh        /usr/local/lib/vpn/common.sh
COPY docker/vpn-supervisor   /usr/local/bin/vpn-supervisor
COPY russia/vpn              /usr/local/bin/vpn
COPY russia/entrypoint.sh    /usr/local/bin/entrypoint
COPY russia/config.json.tmpl /etc/vpn/config.json.tmpl

RUN chmod 755 /usr/local/bin/vpn-supervisor \
              /usr/local/bin/vpn \
              /usr/local/bin/entrypoint

EXPOSE 443/tcp

ENTRYPOINT ["/usr/local/bin/entrypoint"]
CMD []
