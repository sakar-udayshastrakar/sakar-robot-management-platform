#!/bin/bash
exec python -X utf8 -I "$(dirname "$0")/dexq.py" "$@"
