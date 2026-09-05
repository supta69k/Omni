#!/usr/bin/env bash
# Thin JSON-RPC client for the locally-running Figma Dev Mode MCP server.
# Usage: figma.sh <tool_name> '<json args>'
set -euo pipefail

URL="http://127.0.0.1:3845/mcp"
CT="Content-Type: application/json"
AC="Accept: application/json, text/event-stream"

SID=$(curl -s -m 20 -D - -o /dev/null -X POST "$URL" -H "$CT" -H "$AC" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"cc","version":"1.0"}}}' \
  | tr -d '\r' | sed -n 's/^mcp-session-id: //p')

curl -s -m 20 -o /dev/null -X POST "$URL" -H "$CT" -H "$AC" -H "mcp-session-id: $SID" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

if [ "$1" = "list" ]; then
  BODY='{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
else
  BODY=$(printf '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"%s","arguments":%s}}' "$1" "$2")
fi

curl -s -m 120 -X POST "$URL" -H "$CT" -H "$AC" -H "mcp-session-id: $SID" -d "$BODY" \
  | sed -n 's/^data: //p'
