#!/bin/bash

# --- НАЛАШТУВАННЯ ---
USB_PATH="/media/mentalmcenter/B869-F75F"
BACKUP_DIR="$USB_PATH/mmc_backups"

# Дані бази
DB_USER="mmc_db_user"
DB_NAME="psychotherapy"
CONTAINER_NAME="mmc-postgres"

DATE=$(date +"%Y-%m-%d_%H-%M-%S")
UPLOADS_SOURCE="$HOME/MMC/uploads"

mkdir -p "$BACKUP_DIR"

# --- ЕТАП 1: ДАМП БАЗИ ДАНИХ (Історія на 7 днів) ---
docker exec $CONTAINER_NAME pg_dump -U $DB_USER $DB_NAME | gzip > "$BACKUP_DIR/db_$DATE.sql.gz"

# --- ЕТАП 2: ДЗЕРКАЛО МЕДІА (Завжди актуальний стан, без дублів) ---
rsync -rt --delete "$UPLOADS_SOURCE/" "$BACKUP_DIR/uploads_mirror/"

# --- ЕТАП 3: ОЧИЩЕННЯ СТАРИХ БАЗ ДАНИХ ---
find "$BACKUP_DIR" -maxdepth 1 -name "db_*.sql.gz" -type f -mtime +7 -delete