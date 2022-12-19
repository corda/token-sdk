FROM azul/zulu-openjdk:8
RUN apt-get update && apt-get install -y curl apt-transport-https \
                                                  ca-certificates \
                                                  curl \
                                                  gnupg2 \
                                                  software-properties-common \
                                                  wget
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -
RUN add-apt-repository \
       "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
       $(lsb_release -cs) \
       stable"
RUN apt-get update && apt-get install -y docker-ce-cli

RUN SUFFIX= && DEBIAN_ARCH="$(dpkg --print-architecture)" && DEST=/tmp/snyk \
 && if [ "${DEBIAN_ARCH}" != amd64 ]; then SUFFIX="-${DEBIAN_ARCH}"; fi \
 && curl -o "${DEST}" -#L "https://github.com/snyk/cli/releases/latest/download/snyk-linux${SUFFIX}" \
 && for item in snyk snyk-linux; do install -v -T --mode=0755 "${DEST}" "/usr/local/bin/${item}"; done \
 && rm "${DEST}"

RUN DEBIAN_ARCH="$(dpkg --print-architecture)" && DEST=/tmp/snyk-to-html \
 && if [ "${DEBIAN_ARCH}" != amd64 ]; then echo "There is no binary for ${DEBIAN_ARCH} architecture, skipping installation"; exit 0; fi \
 && curl -o "${DEST}" -#L  "https://github.com/snyk/snyk-to-html/releases/latest/download/snyk-to-html-linux" \
 && for item in snyk-to-html snyk-to-html-linux; do install -v -T --mode=0755 "${DEST}" "/usr/local/bin/${item}"; done \
 && rm "${DEST}"

RUN DEBIAN_ARCH="$(dpkg --print-architecture)" && DEST=/tmp/snyk-delta \
 && if [ "${DEBIAN_ARCH}" != amd64 ]; then echo "There is no binary for ${DEBIAN_ARCH} architecture, skipping installation"; exit 0; fi \
 && curl -o "${DEST}" "https://github.com/snyk-tech-services/snyk-delta/releases/latest/download/snyk-delta-linux" \
 && for item in snyk-delta snyk-delta-linux; do install -v -T --mode=0755 "${DEST}" "/usr/local/bin/${item}"; done \
 && rm "${DEST}"

ARG USER="stresstester"
RUN useradd -m ${USER}
