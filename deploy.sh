#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly COMPOSE_FILE="${SCRIPT_DIR}/compose.yaml"
readonly BUILD_COMPOSE_FILE="${SCRIPT_DIR}/compose.backend-build.yaml"
readonly ENV_FILE="${SCRIPT_DIR}/.env.docker"
readonly BACKEND_DIR="${SCRIPT_DIR}/rag-demo-backend"
readonly PROJECT_NAME="${1:-rag-demo}"

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "缺少命令：$1" >&2
        exit 1
    fi
}

env_value() {
    local name="$1"

    if [[ -n "${!name+x}" ]]; then
        printf '%s\n' "${!name}"
        return
    fi

    awk -F= -v key="$1" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "${ENV_FILE}"
}

java_major_version() {
    "$1" -XshowSettings:properties -version 2>&1 \
        | awk -F'= ' '/^[[:space:]]*java\.specification\.version = / {print $2; exit}'
}

configure_java() {
    local java_home_candidate=""

    if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]] \
        && [[ "$(java_major_version "${JAVA_HOME}/bin/java")" == "25" ]]; then
        return
    fi

    if command -v java >/dev/null 2>&1 \
        && [[ "$(java_major_version "$(command -v java)")" == "25" ]]; then
        unset JAVA_HOME
        return
    fi

    if [[ -x /usr/libexec/java_home ]]; then
        java_home_candidate="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
        if [[ -n "${java_home_candidate}" && -x "${java_home_candidate}/bin/java" ]] \
            && [[ "$(java_major_version "${java_home_candidate}/bin/java")" == "25" ]]; then
            export JAVA_HOME="${java_home_candidate}"
            export PATH="${JAVA_HOME}/bin:${PATH}"
            echo "已自动使用 JDK 25：${JAVA_HOME}"
            return
        fi
    fi

    for java_home_candidate in \
        /opt/homebrew/opt/openjdk@25 \
        /usr/local/opt/openjdk@25; do
        if [[ -x "${java_home_candidate}/bin/java" ]] \
            && [[ "$(java_major_version "${java_home_candidate}/bin/java")" == "25" ]]; then
            export JAVA_HOME="${java_home_candidate}"
            export PATH="${JAVA_HOME}/bin:${PATH}"
            echo "已自动使用 JDK 25：${JAVA_HOME}"
            return
        fi
    done

    echo "未找到 JDK 25，请设置 JAVA_HOME 后重试。" >&2
    exit 1
}

require_env_value() {
    local name="$1"
    local value
    value="$(env_value "${name}")"
    if [[ -z "${value//[[:space:]]/}" || "${value}" == CHANGE_ME* ]]; then
        echo ".env.docker 缺少有效配置：${name}" >&2
        exit 1
    fi
}

wait_for_url() {
    local name="$1"
    local url="$2"
    local attempts="${3:-60}"
    local attempt

    for ((attempt = 1; attempt <= attempts; attempt++)); do
        if curl --fail --silent --show-error --output /dev/null --max-time 3 "${url}"; then
            echo "${name}已就绪：${url}"
            return 0
        fi
        sleep 2
    done

    echo "${name}在限定时间内未就绪：${url}" >&2
    echo "查看日志：docker compose --project-name ${PROJECT_NAME} --file ${COMPOSE_FILE} logs --follow" >&2
    return 1
}

require_command docker
require_command curl
require_command awk
configure_java

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "缺少 ${ENV_FILE}" >&2
    echo "请先执行：cp ${SCRIPT_DIR}/deploy.env.example ${ENV_FILE}" >&2
    echo "然后填写真实的数据库、Redis、JWT 和百炼配置。" >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "Docker 服务不可用，请先启动 Docker。" >&2
    exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
    echo "当前 Docker 未安装 Compose 插件。" >&2
    exit 1
fi

for required_name in \
    DB_URL DB_USERNAME DB_PASSWORD \
    REDIS_HOST REDIS_PORT \
    JWT_SECRET DASHSCOPE_API_KEY RAG_DOCKER_DATA_ROOT; do
    require_env_value "${required_name}"
done

readonly DATA_ROOT="$(env_value RAG_DOCKER_DATA_ROOT)"
readonly FRONTEND_PORT="$(env_value FRONTEND_PORT)"
readonly BACKEND_PORT="$(env_value BACKEND_PORT)"

mkdir -p "${DATA_ROOT}/uploads" "${DATA_ROOT}/log"

echo "正在打包 rag-demo-backend..."
"${BACKEND_DIR}/mvnw" -f "${BACKEND_DIR}/pom.xml" -DskipTests package
artifact_name="$(
    "${BACKEND_DIR}/mvnw" -f "${BACKEND_DIR}/pom.xml" help:evaluate \
        -Dexpression=project.build.finalName -q -DforceStdout
)"

readonly JAR_PATH="${BACKEND_DIR}/target/${artifact_name}.jar"
if [[ ! -f "${JAR_PATH}" ]]; then
    echo "未找到打包产物：${JAR_PATH}" >&2
    exit 1
fi

export RAG_DEMO_JAR_FILE="target/${artifact_name}.jar"

readonly -a COMPOSE_BASE=(
    docker compose
    --project-name "${PROJECT_NAME}"
    --env-file "${ENV_FILE}"
    --file "${COMPOSE_FILE}"
)
readonly -a COMPOSE_BUILD=(
    "${COMPOSE_BASE[@]}"
    --file "${BUILD_COMPOSE_FILE}"
)

echo "正在构建前后端镜像..."
"${COMPOSE_BUILD[@]}" build rag-demo-backend rag-demo-frontend

echo "正在加入 Compose 项目：${PROJECT_NAME}"
"${COMPOSE_BASE[@]}" up --detach --no-build

wait_for_url "后端" "http://localhost:${BACKEND_PORT:-8080}/actuator/health"
wait_for_url "前端" "http://localhost:${FRONTEND_PORT:-3000}"

echo "发布完成：http://localhost:${FRONTEND_PORT:-3000}"
echo "后端健康检查：http://localhost:${BACKEND_PORT:-8080}/actuator/health"
echo "查看日志：docker compose --project-name ${PROJECT_NAME} --file ${COMPOSE_FILE} logs --follow"
echo "停止服务：docker compose --project-name ${PROJECT_NAME} --file ${COMPOSE_FILE} down"
