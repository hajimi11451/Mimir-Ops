#!/bin/bash

# 加载环境变量
if [ -f .env ]; then
  export $(cat .env | grep -v '^#' | xargs)
else
  echo "错误：找不到 .env 文件"
  exit 1
fi

# 检查必需的环境变量
required_vars=("DB_PASSWORD" "JWT_SECRET" "ENCRYPTION_KEY")
for var in "${required_vars[@]}"; do
  if [ -z "${!var}" ]; then
    echo "错误：环境变量 $var 未设置"
    exit 1
  fi
done

# 启动应用
echo "正在启动灵枢运维平台后端服务..."
./mvnw spring-boot:run
