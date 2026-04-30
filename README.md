# RAG 企业知识库系统

> Spring Boot + LangChain4j + PostgreSQL/pgvector 构建的企业级 RAG（检索增强生成）知识库问答系统

---

## 一、项目概述

### 1.1 项目背景

在 2026 年的企业应用场景中，"让 AI 能回答企业内部知识"已经成为标配需求。传统的关键词搜索无法理解语义，而直接使用大模型又会因为缺乏企业私有知识而产生"幻觉"。RAG（Retrieval-Augmented Generation，检索增强生成）技术正是解决这一问题的业界标准方案。

本系统以 **Java 后端为核心**，基于 LangChain4j 将文档解析、文本分块、向量检索、LLM 生成等环节无缝串联，构建了一套完整的企业知识库问答系统。

### 1.2 核心价值

| 痛点 | 方案 | 效果 |
|------|------|------|
| 企业内部文档无法被 AI 利用 | 自动解析 + 向量化索引 | 支持 PDF/Word/Excel 等 20+ 格式 |
| 大模型"不懂"企业业务 | RAG 实时检索企业知识 | 回答基于企业文档，幻觉率大幅降低 |
| 员工反复问相同问题 | 多轮对话 + 历史记忆 | 上下文连续对话，提升效率 |
| 纯 Python 方案与 Java 技术栈割裂 | LangChain4j 全 Java 方案 | 零语言切换，Spring 生态无缝集成 |

### 1.3 技术亮点

- **全 Java 技术栈**：基于 LangChain4j，与 Python LangChain 功能对等，Java 后端无需学习新语言
- **OpenAI 兼容协议**：同时支持 DeepSeek、通义千问、硅基流动、OpenAI 等多个 LLM 提供商，一键切换
- **pgvector 向量存储**：与业务数据库 PostgreSQL 共用，无需额外部署 Milvus / Pinecone，运维成本最低
- **Apache Tika 文档解析**：覆盖 PDF、Word、Excel、PPT、Markdown、HTML、TXT 等主流格式
- **多轮对话记忆**：完整的会话管理，支持上下文连续对话
- **生产级工程结构**：全局异常处理、参数校验、CORS、Swagger 文档一应俱全

---

## 二、系统架构

### 2.1 架构图

```
+-------------+     +------------------+     +-------------------+
|   前端/客户端  | --> |   Spring Boot     | --> |   PostgreSQL       |
|  (Swagger/   |     |   REST API        |     |   + pgvector       |
|   Postman)   | <-- |   (Java 21)       | <-- |   (向量 + 业务数据)  |
+-------------+     +------------------+     +-------------------+
                           |        ^
                           v        |
                    +-------------+  |
                    | DeepSeek /   |--+
                    | 通义千问 API  |
                    | (LLM 推理)   |
                    +-------------+
```

### 2.2 技术栈

| 层级 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 基础框架 | Spring Boot | 3.4.1 | IoC / MVC / JPA |
| AI 框架 | LangChain4j | 0.36.2 | LLM 集成 / 文档处理 / 向量存储 |
| LLM 对话 | DeepSeek Chat API | - | OpenAI 兼容协议，可替换 |
| 向量化 | DashScope / SiliconFlow | - | text-embedding-v2 / bge-large-zh |
| 向量数据库 | pgvector | pg16 | PostgreSQL 向量扩展 |
| 文档解析 | Apache Tika | 2.9+ | 20+ 文档格式支持 |
| 数据库 | PostgreSQL | 16 | 业务数据 + 向量数据统一存储 |
| 接口文档 | SpringDoc OpenAPI | 2.7.0 | Swagger UI |
| 构建工具 | Maven | 3.9+ | 依赖管理 |
| 运行时 | Java | 21 LTS | 虚拟线程 / 文本块 / 记录类 |

### 2.3 模块划分

```
com.example.rag
├── config/           # 配置层
│   ├── AiConfig      # LLM + Embedding + pgvector Bean 配置
│   └── WebConfig     # CORS 跨域配置
├── controller/       # 控制器层
│   ├── DocumentController  # 文档上传 / 查询 / 删除
│   └── ChatController      # 对话 / 会话管理
├── service/          # 业务逻辑层
│   ├── DocumentService     # 文档解析 → 分块 → 向量化 → 存储
│   └── ChatService         # RAG 检索 + 多轮对话 + LLM 生成
├── repository/       # 数据访问层
│   ├── DocumentRepository
│   ├── ConversationRepository
│   └── MessageRepository
├── model/
│   ├── entity/       # JPA 实体
│   └── dto/          # 请求/响应 DTO
└── exception/        # 全局异常处理
```

---

## 三、核心功能详解

### 3.1 文档索引流程

```
用户上传文件 (MultipartFile)
    │
    ▼
┌──────────────────────┐
│  Apache Tika 解析     │  ← 提取纯文本内容 (PDF/Word/Excel...)
│  parseToString()     │
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│  递归文本分块          │  ← DocumentSplitters.recursive(500, 50)
│  (500字/块, 50字重叠) │     保持语义完整性的同时避免超长
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│  Embedding 向量化     │  ← EmbeddingModel (OpenAI 兼容)
│  文本 → 1536维向量     │     每块文本生成一个高维向量
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│  存入 pgvector       │  ← PgVectorEmbeddingStore
│  (向量 + 元数据)       │     向量存 IVFFlat 索引，元数据含 document_id
└──────────────────────┘
```

### 3.2 RAG 问答流程

```
用户提问: "公司年假怎么申请?"
    │
    ▼
┌──────────────────────┐
│  查询向量化            │  ← 将问题转为 1536 维向量
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│  pgvector 相似度检索   │  ← 余弦相似度 top-K 检索
│  (IVFFlat 近似检索)   │     默认返回 top-5，过滤相似度 < 0.7
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│  构建增强 Prompt      │  ← 系统提示词 + 知识库上下文 + 对话历史 + 当前问题
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│  DeepSeek LLM 生成    │  ← 基于知识库内容给出精准回答
│  (ChatLanguageModel)  │     无相关知识时如实告知，绝不编造
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│  保存对话记录          │  ← 完整保存 USER/ASSISTANT 消息
│  返回结果 + 引用来源   │     附带检索来源，可追溯
└──────────────────────┘
```

### 3.3 多轮对话记忆

系统维护完整的对话历史。每次提问时，LLM 会接收：
1. **系统提示词**（含知识库检索到的上下文）
2. **历史消息**（之前全部 USER/ASSISTANT 对话）
3. **当前用户消息**

会话自动命名（取第一条消息前 40 字），支持增删查。

### 3.4 支持的文档格式

通过 Apache Tika，支持以下格式的自动解析：

| 类别 | 格式 |
|------|------|
| 办公文档 | PDF, Word (.doc/.docx), Excel (.xls/.xlsx), PowerPoint (.ppt/.pptx) |
| 文本 | TXT, Markdown (.md), CSV, JSON, XML |
| 网页 | HTML, HTM |
| 电子书 | EPUB |

---

## 四、API 接口文档

启动后访问 `http://localhost:8080/swagger-ui.html` 可在线调试。

### 4.1 文档管理

#### 上传文档
```
POST /api/documents/upload
Content-Type: multipart/form-data
参数: file (文件)

Response 200:
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "fileName": "员工手册.pdf",
    "fileType": "pdf",
    "fileSize": 2048576,
    "chunkCount": 42,
    "status": "INDEXED",
    "createdAt": "2026-04-28T17:00:00"
  }
}
```

#### 查询文档列表
```
GET /api/documents
Response: ApiResponse<List<DocumentInfo>>
```

#### 删除文档
```
DELETE /api/documents/{id}
Response: ApiResponse<Void>
// 同时删除向量库中的关联数据
```

### 4.2 对话管理

#### 发送消息
```
POST /api/chat/send
Content-Type: application/json

{
  "conversationId": null,    // null 则创建新会话
  "message": "公司年假怎么申请?"
}

Response 200:
{
  "code": 200,
  "message": "success",
  "data": {
    "conversationId": 1,
    "message": "根据公司《员工手册》第三章第5条...",
    "sources": [
      "[文档1·片段3] 相似度:0.92",
      "[文档1·片段5] 相似度:0.87",
      "[文档2·片段0] 相似度:0.81"
    ]
  }
}
```

#### 获取对话列表
```
GET /api/chat/conversations
Response: ApiResponse<List<Conversation>>
```

#### 获取对话历史
```
GET /api/chat/conversations/{id}/messages
Response: ApiResponse<List<Message>>
```

#### 删除对话
```
DELETE /api/chat/conversations/{id}
Response: ApiResponse<Void>
```

### 4.3 统一响应格式

```json
{
  "code": 200,          // 200 成功, 400 业务异常, 500 服务错误
  "message": "success", // 提示信息
  "data": {}            // 业务数据
}
```

---

## 五、快速开始

### 5.1 环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 21 LTS | 运行 javac/java |
| Maven | 3.9+ | 构建工具 |
| Docker | 20+ | 运行 PostgreSQL + pgvector |
| DeepSeek API Key | - | https://platform.deepseek.com |
| Embedding API Key | - | DashScope 或 SiliconFlow |

### 5.2 启动数据库

```bash
cd rag-knowledge-base
docker-compose up -d

# 验证 pgvector 扩展
docker exec -it rag-kb-postgres psql -U postgres -d rag_kb -c "SELECT extname FROM pg_extension;"
# 应输出: vector
```

### 5.3 配置 API Key

编辑 `src/main/resources/application.yml`，或设置环境变量：

```bash
# 方式一：环境变量
export DEEPSEEK_API_KEY=sk-xxxxxxxx
export EMBEDDING_API_KEY=sk-xxxxxxxx

# 方式二：直接修改 application.yml 中的 api-key
```

**推荐：使用硅基流动 (SiliconFlow) 一个 Key 搞定对话 + 向量化**

修改 `application.yml`：
```yaml
app:
  ai:
    chat:
      api-key: ${SILICONFLOW_API_KEY}
      base-url: https://api.siliconflow.cn/v1
      model-name: deepseek-ai/DeepSeek-V3
    embedding:
      api-key: ${SILICONFLOW_API_KEY}
      base-url: https://api.siliconflow.cn/v1
      model-name: BAAI/bge-large-zh-v1.5
      dimension: 1024
```

### 5.4 启动应用

```bash
# 本机如果默认 JDK 8，需指定 JDK 21
export JAVA_HOME="C:/Program Files/Java/jdk-21"
mvn spring-boot:run

# 启动日志:
# Tomcat started on port 8080
# Started RagApplication in 4.2 seconds
```

### 5.5 验证流程

1. 打开 http://localhost:8080/swagger-ui.html
2. 调用 `POST /api/documents/upload` 上传一份公司文档（如 PDF）
3. 看到 `status: "INDEXED"` 说明向量化完成
4. 调用 `POST /api/chat/send`，发送 "这份文档主要讲了什么?"
5. AI 基于文档内容回答问题

---

## 六、配置说明

### 6.1 主要配置项 (`application.yml`)

```yaml
app:
  ai:
    chat:
      api-key: ${DEEPSEEK_API_KEY}      # DeepSeek API 密钥
      base-url: https://api.deepseek.com # API 地址（可替换）
      model-name: deepseek-chat          # 模型名称
      temperature: 0.7                   # 生成随机性 (0-2)
      max-tokens: 2000                   # 最大输出长度
    embedding:
      api-key: ${EMBEDDING_API_KEY}      # Embedding 服务密钥
      base-url: ...                      # 向量化服务地址
      model-name: text-embedding-v2      # Embedding 模型
      dimension: 1536                    # 向量维度（模型相关）
    rag:
      top-k: 5                           # 检索返回条数
      similarity-threshold: 0.7          # 相似度阈值 (0-1)
      chunk-size: 500                    # 文本分块大小
      chunk-overlap: 50                  # 分块重叠字数
```

### 6.2 大模型切换指南

系统基于 OpenAI 兼容协议，任何提供 `/v1/chat/completions` 和 `/v1/embeddings` 接口的服务都可以无缝替换：

| 提供商 | Chat 模型 | Embedding 模型 | 备注 |
|--------|-----------|----------------|------|
| DeepSeek | deepseek-chat | - | 官网注册，10 元能用很久 |
| DashScope | qwen-turbo | text-embedding-v2 | 阿里云，有免费额度 |
| SiliconFlow | deepseek-ai/DeepSeek-V3 | BAAI/bge-large-zh-v1.5 | 一个 Key 搞定全部，推荐 |
| OpenAI | gpt-4o | text-embedding-3-small | 需科学上网 |
| 通义千问 | qwen-plus | text-embedding-v2 | 阿里云百炼平台 |

---

## 七、数据库设计

### 7.1 JPA 实体表（Spring Data JPA 自动建表）

**documents（文档元数据表）**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键 |
| file_name | VARCHAR | 原始文件名 |
| file_type | VARCHAR | 文件类型 (pdf/doc/xlsx...) |
| file_size | BIGINT | 文件大小 (bytes) |
| content | TEXT | 文档全文 |
| chunk_count | INTEGER | 分块数量 |
| status | VARCHAR(20) | INDEXING / INDEXED / FAILED |
| error_message | VARCHAR(500) | 错误信息 |
| created_at | TIMESTAMP | 创建时间 |

**conversations（会话表）**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键 |
| title | VARCHAR(100) | 会话标题（自动命名） |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 最后活跃时间 |

**messages（消息表）**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键 |
| conversation_id | BIGINT | 关联会话 ID |
| role | VARCHAR(20) | USER / ASSISTANT |
| content | TEXT | 消息内容 |
| created_at | TIMESTAMP | 发送时间 |

### 7.2 向量存储表（LangChain4j 自动管理）

**document_chunks（文档向量块，pgvector）**
| 字段 | 类型 | 说明 |
|------|------|------|
| embedding_id | UUID | 主键 |
| embedding | vector(1536) | 向量数据 |
| text | TEXT | 分块文本 |
| metadata | JSONB | 元数据 (document_id, chunk_index) |

索引：`IVFFlat` 近似最近邻索引，用于加速向量相似度检索。

---

## 八、部署方案

### 8.1 Docker Compose 部署（推荐）

```yaml
# 1. 打包
mvn clean package -DskipTests

# 2. 在 docker-compose.yml 中添加应用服务
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY}
      - EMBEDDING_API_KEY=${EMBEDDING_API_KEY}
    depends_on:
      postgres:
        condition: service_healthy
```

### 8.2 传统部署

```bash
# 打包
mvn clean package -DskipTests

# 启动
java -jar \
  -DDEEPSEEK_API_KEY=sk-xxx \
  -DEMBEDDING_API_KEY=sk-xxx \
  target/rag-knowledge-base-1.0.0.jar
```

---

## 九、扩展方向

完成基础版本后，可以在此基础上继续增强：

| 扩展方向 | 技术方案 | 简历加分 |
|----------|----------|----------|
| 流式输出 (SSE) | Spring WebFlux + Flux | 展示对异步编程的理解 |
| 权限控制 | Spring Security + JWT | 企业级安全能力 |
| 多知识库隔离 | namespace 元数据过滤 | 复杂业务建模能力 |
| Agent 工具调用 | LangChain4j AiServices + @Tool | AI Agent 实战经验 |
| MCP 协议支持 | Spring AI MCP 模块 | 2026 年前沿技术热点 |
| 图片理解 | 多模态模型 (GPT-4V) | 多模态 AI 经验 |
| 监控面板 | Micrometer + Prometheus | 可观测性工程能力 |

---

## 十、常见问题

**Q: pgvector 扩展安装失败？**
```sql
-- 如果 Docker 镜像不带 pgvector，手动安装：
CREATE EXTENSION vector;
-- 如果报错，使用 pgvector/pg16 镜像而非 postgres 官方镜像
```

**Q: API 调用超时？**
修改 `AiConfig.java` 中的 `timeout(Duration.ofSeconds(120))` 值。

**Q: 检索相似度都很低？**
- 降低 `similarity-threshold` 阈值
- 减小 `chunk-size` 分块大小
- 检查文档是否有实质内容

**Q: 中文 Embedding 效果差？**
换用中文优化的模型：`BAAI/bge-large-zh-v1.5` 或 `text-embedding-v2`

---

## 十一、版本记录

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | 2026-04-28 | 初始版本：文档索引、RAG 问答、多轮对话 |

---

## License

MIT
