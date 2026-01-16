# Docker で SchemaSpy (Mermaid Markdown対応版) を使う

## クイックスタート

### 1. ソースからDockerイメージをビルド

```bash
# リポジトリのルートで実行
docker build -f Dockerfile.build -t schemaspy-markdown .
```

### 2. 実行例

#### MySQL

```bash
docker run --rm \
  -v "$PWD/output:/output" \
  --network host \
  schemaspy-markdown \
  -t mysql \
  -host localhost \
  -port 3306 \
  -db mydb \
  -u root \
  -p password \
  -markdown
```

#### PostgreSQL

```bash
docker run --rm \
  -v "$PWD/output:/output" \
  --network host \
  schemaspy-markdown \
  -t pgsql \
  -host localhost \
  -port 5432 \
  -db mydb \
  -u postgres \
  -p password \
  -markdown
```

#### SQLite

```bash
docker run --rm \
  -v "$PWD/output:/output" \
  -v "$PWD/mydb.sqlite:/data/mydb.sqlite:ro" \
  schemaspy-markdown \
  -t sqlite-xerial \
  -db /data/mydb.sqlite \
  -o /output \
  -markdown \
  -sso
```

### 3. Markdownのみ出力（HTMLなし）

```bash
docker run --rm \
  -v "$PWD/output:/output" \
  --network host \
  schemaspy-markdown \
  -t mysql \
  -host localhost \
  -db mydb \
  -u root \
  -p password \
  -markdown \
  -nohtml
```

## 出力ファイル

```
output/
├── schema.md          # Mermaid ER図を含むMarkdownファイル
├── index.html         # HTMLレポート（-nohtmlを指定しない場合）
├── diagrams/          # 図の画像ファイル
└── ...
```

## docker-compose での使用

### docker-compose.yml

```yaml
version: '3.8'

services:
  # サンプル: MySQL データベース
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: sampledb
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  # SchemaSpy でMarkdown生成
  schemaspy:
    build:
      context: .
      dockerfile: Dockerfile.build
    depends_on:
      mysql:
        condition: service_healthy
    volumes:
      - ./output:/output
    command: >
      -t mysql
      -host mysql
      -port 3306
      -db sampledb
      -u root
      -p password
      -markdown

volumes:
  mysql_data:
```

### 実行

```bash
# データベース起動 & SchemaSpy実行
docker-compose up schemaspy

# 出力確認
cat output/schema.md
```

## オプション一覧

| オプション | 説明 |
|-----------|------|
| `-markdown` | Mermaid ER図を含むMarkdownファイルを生成 |
| `-nohtml` | HTML出力をスキップ（Markdownのみ生成） |
| `-noimplied` | 暗黙的なリレーションシップを除外 |
| `-noorphans` | 孤立テーブルを除外 |

## 生成されるMarkdownの例

```markdown
# Database Schema: sampledb

## Entity Relationship Diagram

\`\`\`mermaid
erDiagram
    users {
        int id PK
        varchar name
        varchar email UK
    }
    posts {
        int id PK
        int user_id FK
        varchar title
    }
    users ||--o{ posts : "FK"
\`\`\`

## Table Details

### users

| Column | Type | Nullable | Key |
|--------|------|----------|-----|
| id | int | NO | PK |
| name | varchar | NO | |
| email | varchar | YES | UK |
```

## トラブルシューティング

### ホストのデータベースに接続できない

Mac/Windows の場合:
```bash
docker run --rm \
  -v "$PWD/output:/output" \
  schemaspy-markdown \
  -t mysql \
  -host host.docker.internal \  # localhost ではなく host.docker.internal
  -db mydb \
  -u root \
  -p password \
  -markdown
```

Linux の場合:
```bash
docker run --rm \
  -v "$PWD/output:/output" \
  --network host \              # --network host を追加
  schemaspy-markdown \
  -t mysql \
  -host localhost \
  -db mydb \
  -u root \
  -p password \
  -markdown
```

### JDBCドライバーを追加したい

```bash
docker run --rm \
  -v "$PWD/output:/output" \
  -v "$PWD/my-drivers:/drivers" \  # カスタムドライバーをマウント
  schemaspy-markdown \
  -t mydb \
  -host localhost \
  -db mydb \
  -markdown
```
