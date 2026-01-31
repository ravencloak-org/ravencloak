FROM quay.io/keycloak/keycloak:26.5.0

ARG SPI_VERSION=0.0.1-SNAPSHOT
ARG GITHUB_REPO=your-org/auth

# Download SPI JAR from GitHub Releases
ADD https://github.com/${GITHUB_REPO}/releases/download/v${SPI_VERSION}/keycloak-user-storage-spi-${SPI_VERSION}.jar /opt/keycloak/providers/

# Build optimized Keycloak with the provider
RUN /opt/keycloak/bin/kc.sh build
