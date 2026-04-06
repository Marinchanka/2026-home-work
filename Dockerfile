FROM ubuntu:22.04

WORKDIR /tmp

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    build-essential=12.9ubuntu3 \
    libreadline-dev=8.1.2-1 \
    curl=7.81.0-1ubuntu1.20 \
    git=1:2.34.1-1ubuntu1.11 \
    libssl-dev=3.0.2-0ubuntu1.18 \
    libz-dev=1:1.2.11.dfsg-2ubuntu9.2 \
    wget=1.21.2-2ubuntu1 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /tmp

RUN wget https://www.lua.org/ftp/lua-5.3.5.tar.gz && \
    tar -zxf lua-5.3.5.tar.gz && \
    cd lua-5.3.5 && \
    make linux test && \
    make install

WORKDIR /tmp

RUN git clone https://github.com/AmpereTravis/wrk2-aarch64.git wrk2 && \
    cd wrk2 && \
    make && \
    cp wrk /usr/local/bin/

WORKDIR /wrk2
ENTRYPOINT ["wrk"]
CMD ["--version"]