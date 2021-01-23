FROM openjdk:14 AS builder

ARG version

WORKDIR /crashlogbot

COPY assets/CrashLogBot-${version}.jar crashlogbot.jar

ENV ADDITIONAL_MODULES=jdk.crypto.ec

RUN ["bash", "crashlogbot.jar"]

FROM frolvlad/alpine-glibc:alpine-3.9

ARG jattachVersion

WORKDIR /mantaro

RUN apk add --no-cache libstdc++

RUN wget "https://www.archlinux.org/packages/core/x86_64/zlib/download" -O /tmp/libz.tar.xz \
    && mkdir -p /tmp/libz \
    && tar -xf /tmp/libz.tar.xz -C /tmp/libz \
    && cp /tmp/libz/usr/lib/libz.so.1.2.11 /usr/glibc-compat/lib \
    && /usr/glibc-compat/sbin/ldconfig \
    && rm -rf /tmp/libz /tmp/libz.tar.xz

RUN wget https://github.com/apangin/jattach/releases/download/$jattachVersion/jattach -O /bin/jattach
RUN chmod +x /bin/jattach

COPY assets assets
COPY --from=builder /crashlogbot /crashlogbot

CMD ["jrt/bin/java", "-jar", "crashlogbot.jar"]