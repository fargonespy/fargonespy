ARG TARGETARCH
FROM "ogarcia/archlinux:devel" AS build
ARG USER="builder"

# hadolint ignore=SC2016
RUN if [[ "${TARGETARCH}" == "arm64" ]]; \
  then \
    sed -i '1 i\Server = https://mirror.yandex.ru/archlinux-arm/$arch/$repo' \
    /etc/pacman.d/mirrorlist; \
  elif [[ "{$TARGETARCH}" == "amd64" ]]; \
  then \
    sed -i '1 i\Server = https://mirror.yandex.ru/archlinux/$arch/$repo' \
    /etc/pacman.d/mirrorlist; \
  fi

RUN echo "en_US.UTF-8 UTF-8" > /etc/locale.gen && locale-gen
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

RUN pacman --sync --refresh --noconfirm zsh jdk21-openjdk maven go git \
  && pacman --sync --refresh --sysupgrade --noconfirm && \
  pacman --sync --clean --clean --noconfirm && \
  rm --recursive --force /var/cache/pacman/pkg/* && \
  rm --force /var/log/pacman.log

RUN useradd --shell="/usr/bin/zsh" "${USER}" && \
  echo "${USER} ALL=(ALL) NOPASSWD: ALL" >> "/etc/sudoers"

# Maven build
USER "${USER}"
WORKDIR /home/${USER}/build/java
COPY --chown="${USER}:${USER}" ./pom.xml /home/${USER}/build/java/pom.xml
COPY --chown="${USER}:${USER}" ./src /home/${USER}/build/java/src
COPY --chown="${USER}:${USER}" ./test /home/${USER}/build/java/test
RUN mvn dependency:go-offline
RUN mvn clean package

# Go build
WORKDIR /home/${USER}/build/go
COPY ./dns ./
# hadolint ignore=DL4006
RUN eval "$(go env | grep -e "GOHOSTOS" -e "GOHOSTARCH")" && \
  GOOS="${GOHOSTOS}" GOARCH="${GOHOSTARCH}" \
  go build -x \
    -buildmode="pie" \
    -trimpath \
    -mod="readonly" \
    -modcacherw \
    -ldflags "-linkmode external"

FROM "ogarcia/archlinux:devel" AS gamespy
ARG EVAL='eval "$(direnv hook zsh)"'
ARG PROMPT='"%(?.%F{green}√.%F{red}?%?)%f %B%F{240}%1~%f@%F{cyan}${HOSTNAME}%f%b %# "'
ARG APP="fargonespy"

# hadolint ignore=SC2016
RUN if [[ "${TARGETARCH}" == "arm64" ]]; \
  then \
    sed -i '1 i\Server = https://mirror.yandex.ru/archlinux-arm/$arch/$repo' \
    /etc/pacman.d/mirrorlist; \
  elif [[ "{$TARGETARCH}" == "amd64" ]]; \
  then \
    sed -i '1 i\Server = https://mirror.yandex.ru/archlinux/$arch/$repo' \
    /etc/pacman.d/mirrorlist; \
  fi

RUN echo "en_US.UTF-8 UTF-8" > /etc/locale.gen && locale-gen
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

RUN pacman --sync --refresh --noconfirm zsh direnv jre21-openjdk-headless \
  && pacman --sync --refresh --sysupgrade --noconfirm && \
  pacman --sync --clean --clean --noconfirm && \
  rm --recursive --force /var/cache/pacman/pkg/* && \
  rm --force /var/log/pacman.log

RUN useradd --shell="/usr/bin/zsh" "${APP}" && \
  echo "${APP} ALL=(ALL) NOPASSWD: ALL" >> "/etc/sudoers"

USER "${APP}"
WORKDIR "/home/${APP}"
COPY --chown=${APP}:${APP} --chmod=0755 entrypoint.sh ./entrypoint.sh
COPY --chown=${APP}:${APP} --chmod=0644 --from=build /home/builder/build/java/target/${APP}-*.jar ./${APP}.jar
COPY --chown=${APP}:${APP} --chmod=0755 --from=build /home/builder/build/go/dns ./dns

ENV JAVA_HOME="/usr/lib/jvm/default-runtime"
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV SHELL="/usr/bin/zsh"
ENV HOSTNAME="${APP}"
EXPOSE 53/udp
EXPOSE 53/tcp
EXPOSE 80/tcp
EXPOSE 443/tcp
EXPOSE 27900/udp
EXPOSE 27901/udp
EXPOSE 28910/tcp
EXPOSE 29900/tcp
EXPOSE 29901/tcp
EXPOSE 29920/tcp

RUN echo "PROMPT=${PROMPT}" >> ~/.zshrc && \
  echo "${EVAL}" >> ~/.zshrc
ENTRYPOINT ["./entrypoint.sh"]
