#!/bin/sh
DIR="$(cd "$(dirname "$0")" && pwd)"
exec /tmp/gradle-8.4/bin/gradle "$@"
