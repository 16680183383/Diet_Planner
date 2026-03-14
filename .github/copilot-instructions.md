## Diet_Planner 开发助手指引

这些规则旨在让 AI 编码助手在本项目中立即高效工作。请遵循本仓库的既有架构、约定与工作流。

**项目概览**
- **框架与运行时**: Spring Boot 3.2, Java 17；Web + Security；Neo4j 图数据库；JWT。
- **模块结构**: 控制器(`src/main/java/.../controller`)、服务(`service`/`service/impl`)、仓库(`repository`)、模型(`model`)、配置(`config`)、DTO/响应(`dto`)、异常(`exception`)、工具(`util`) 清晰分层。
- **数据来源**: 本地 `data/foods/merged.json`、`recipe_corpus_full1.json`；通过 `FoodDataService` / `DataImportController` 导入。
- **AI 集成**: `ai/RecipeAiService.java` 封装与食谱/推荐相关的 AI 逻辑（调用处在 `RecipeServiceImpl`）。

**关键工作流**
- **构建**: 使用 Maven；在项目根目录执行：
  - `./mvnw clean verify`（Windows 用 `mvnw.cmd`）；
  - 运行应用：`mvnw.cmd spring-boot:run`。
- **测试**: `src/test/java/.../DietPlannerApplicationTests.java`；运行：`mvnw.cmd test`。
- **调试**: 通过 `spring-boot:run` 启动后，服务默认监听 HTTP（控制器在 `/api/...` 路径，具体见各 Controller 的 `@RequestMapping`）；Neo4j 需本地 `bolt://localhost:7687`，账号 `neo4j/123456`（来源于 `application.properties`）。

**架构与模式**
- **分层约定**:
  - Controller 仅做入参校验与路由，返回 `dto` 下的响应包装（如 `ApiResponse`, `RecipeResponse`, `FeedbackResponse`）。
  - Service 实现业务编排与调用 AI；接口在 `service`，实现置于 `service/impl`（例如 `RecipeService` 与 `RecipeServiceImpl`）。
  - Repository 仅声明 Neo4j CRUD/查询（如 `RecipeRepository`, `FoodRepository`）。
  - Model 表示领域实体（如 `Recipe`, `Ingredient`, `Food`, `User`, `Feedback`）。
- **DTO/响应包装**:
  - 列表与单项响应统一使用 `dto` 包下封装类（如 `RecipeListResponse`, `ShoppingListResponse`）；新增接口请复用该风格，避免直接暴露实体。
- **安全与认证**:
  - JWT 由 `util/JwtUtils.java` 生成与解析；`config/WebSecurityConfig.java` 定义鉴权规则与过滤链。
  - 新增需要认证的接口，遵循现有 `WebSecurityConfig` 模式，将匿名访问限制至必要端点（如登录/注册）。
- **Neo4j 集成**:
  - `config/Neo4jConfig.java` 提供图数据库配置；关系建模通过 `model/FoodRelationship.java` 等；查询封装在 `repository`。
- **数据导入与调度**:
  - `controller/DataImportController.java` 调用 `FoodDataService` 导入 `data/foods` 下 JSON。
  - `scheduler/KnowledgeUpdateScheduler.java` 定时任务更新知识图（池大小见 `spring.task.scheduling.pool.size`）。

**编码约定**
- **包结构**: 保持与现有分层一致；接口与实现分离，命名 `XxxService` / `XxxServiceImpl`。
- **异常处理**: 统一抛出 `exception/CustomException.java`，捕获与映射由 `GlobalExceptionHandler.java` 完成；新异常场景请复用此机制。
- **返回值规范**: 控制器返回 `ApiResponse<T>` 或现有 `*Response` 类型；避免裸返回实体或 Map。
- **序列化**: 使用 Jackson；DTO 与实体需 Lombok 注解一致性（本项目已启用 Lombok 注解处理器）。

**集成点与跨组件通信**
- **AI 服务**: `ai/RecipeAiService.java` 为核心接入点；若新增推荐/分析能力，请在 Service 层封装，避免直接在 Controller 调用 AI。
- **用户与反馈**: `UserController`, `FeedbackController` 经由 `UserServiceImpl`, `RecipeServiceImpl` 处理；与 JWT 安全链路配合。
- **购物清单**: `ShoppingListController` 使用 `ShoppingListDTO`, `ShoppingListRequest/Response`；遵循 DTO 进出站模式。

**常见任务示例**
- 新增一个受保护的接口：
  - 在 `controller` 中创建 `@RestController`，`@RequestMapping("/api/xxx")`；入参使用 `dto` 请求对象；返回 `ApiResponse` 或 `*Response`。
  - 在 `service` 定义接口与实现；必要时调用 `RecipeAiService` 或对应 `repository`。
  - 如需持久化，新增 `model` 与 `repository` 方法；在 `Neo4j` 中定义关系实体（参考 `FoodRelationship.java`）。
- 导入新数据源：
  - 将 JSON 放入 `data/foods/`；在 `FoodDataService` 增加解析逻辑；通过 `DataImportController` 触发导入；确保字段映射与模型一致。

**运行前置**
- 本地需 Neo4j 启动并可访问：`bolt://localhost:7687`；使用 `application.properties` 中用户名密码。
- JWT 密钥取自 `jwt.secret`（仅开发环境，生产需改为安全管理）。

**文件索引（示例）**
- 应用入口：`DietPlannerApplication.java`
- 安全配置：`config/WebSecurityConfig.java`, `util/JwtUtils.java`
- 图数据库：`config/Neo4jConfig.java`, `repository/*`
- 控制器：`controller/*`（食谱、购物清单、用户、反馈、数据导入）
- 服务：`service/*`, `service/impl/*`
- 定时任务：`scheduler/KnowledgeUpdateScheduler.java`
- 数据：`data/foods/*`

**注意**
- 保持现有响应封装与异常处理一致性；不要直接在 Controller 中操作仓库或 AI。
- 变更安全规则时同步检查 JWT 解析与过滤器链，避免破坏匿名端点。

如有不清楚或可改进的部分（尤其 AI 服务职责边界、调度更新流程、特定端点的认证策略），请指出以便迭代完善。
