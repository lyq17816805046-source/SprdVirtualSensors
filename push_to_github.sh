#!/usr/bin/env bash
set -euo pipefail

# 自动创建公开仓库并推送（无需手动填用户名，脚本自动获取）
# 需先设置环境变量：
#   export GITHUB_TOKEN="你的PAT"      # 必需（至少 repo 权限）
#   export GITHUB_REPO="SprdVirtualSensors" # 可选，默认 SprdVirtualSensors
#   export GITHUB_ORG="你的组织名"      # 可选，创建到组织时设置

REPO="${GITHUB_REPO:-SprdVirtualSensors}"
API="https://api.github.com"
AUTH=("-H" "Authorization: token ${GITHUB_TOKEN}" "-H" "Accept: application/vnd.github+json")

# 自动获取 owner
OWNER="${GITHUB_OWNER:-}"
ORG="${GITHUB_ORG:-}"

if [[ -z "$ORG" ]]; then
  if [[ -z "$OWNER" ]]; then
    echo "[INFO] 未提供 GITHUB_OWNER，自动获取登录用户名..."
    user_json=$(curl -s "${AUTH[@]}" "${API}/user")
    OWNER=$(echo "$user_json" | sed -n 's/.*"login":"\([^"]*\)".*/\1/p')
    [[ -z "$OWNER" ]] && { echo "[ERR] 无法获取用户名，请手动设置 GITHUB_OWNER"; exit 1; }
    echo "[INFO] GITHUB_OWNER=${OWNER}"
  fi
  echo "[INFO] 在个人账号下创建/复用 ${OWNER}/${REPO} ..."
  curl -s -X POST "${AUTH[@]}" "${API}/user/repos" -d "{\"name\":\"${REPO}\", \"private\": false}" >/dev/null || true
  HTML_URL="https://github.com/${OWNER}/${REPO}"
  CLONE_URL="https://github.com/${OWNER}/${REPO}.git"
else
  echo "[INFO] 在组织 ${ORG} 下创建/复用 ${ORG}/${REPO} ..."
  curl -s -X POST "${AUTH[@]}" "${API}/orgs/${ORG}/repos" -d "{\"name\":\"${REPO}\", \"private\": false}" >/dev/null || true
  OWNER="$ORG"
  HTML_URL="https://github.com/${OWNER}/${REPO}"
  CLONE_URL="https://github.com/${OWNER}/${REPO}.git"
fi

# 初始化并推送
git init
git branch -m main || true
git add .
git commit -m "Initial commit: SprdVirtualSensors for Unisoc T760" || true
git remote remove origin 2>/dev/null || true
git remote add origin "$CLONE_URL"
git push -u origin main

echo "[OK] 推送完成：${HTML_URL}"
echo "[INFO] GitHub Actions 将自动开始构建，请查看 Actions 页面。"
