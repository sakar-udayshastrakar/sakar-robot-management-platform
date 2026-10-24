#!/bin/bash
# REA CLI wrapper (published rea-agents@6.7.0 in scratchpad). Usage: rea.sh <command> [args]
export REA_JADX_MCP_JAR="C:/Users/sakar/AppData/Local/Temp/claude/D--Sakar-Robotics-Projects-support-portal/5ba6b382-e7e8-4ce5-92df-45edd177f75a/scratchpad/tools/jadx-headless-mcp-0.7.1-all.jar"
export JAVA_HOME="C:\Program Files\Java\jdk-21.0.11"
export REA_JADX_OPERATION_TIMEOUT_MS=1200000
export REA_JADX_HEAP_MIB=2560
exec node "C:/Users/sakar/AppData/Local/Temp/claude/D--Sakar-Robotics-Projects-support-portal/5ba6b382-e7e8-4ce5-92df-45edd177f75a/scratchpad/rea-pkg/node_modules/rea-agents/scripts/rea.mjs" "$@"
