

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for t_rule
-- ----------------------------
DROP TABLE IF EXISTS `t_rule`;
CREATE TABLE `t_rule`  (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
                           `rule_id` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则编号，如 H-01 / R-02 / D-01',
                           `name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则名称/场景摘要',
                           `dimension` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '维度：hotel / room_type / facilities / terms / confirmation / data_flow',
                           `issue_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '问题类型，如 BED_TYPE_MISMATCH',
                           `priority` int NOT NULL DEFAULT 100 COMMENT '优先级，越大越高：合同1000/专属500/问题100/数据流80/默认10',
                           `evidence_required` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '所需证据（JSON 数组）',
                           `default_liability` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '默认主责：SUPPLIER_PRIMARY / OURS_PRIMARY / CHANNEL_PRIMARY / SHARED / CUSTOMER / FORCE_MAJEURE / CONTRACT / UNDETERMINED',
                           `secondary_liability` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NONE' COMMENT '默认次责：SUPPLIER_SECONDARY / OURS_SECONDARY / CHANNEL_SECONDARY / NONE',
                           `branches_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'firstBadHop 分支裁决（JSON 数组），v2 新增',
                           `content_md` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '规则全文（Markdown/JSON），用于 RAG 切分',
                           `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
                           `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 停用',
                           `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
                           `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
                           PRIMARY KEY (`id`) USING BTREE,
                           UNIQUE INDEX `uk_rule_id_version`(`rule_id` ASC) USING BTREE,
                           INDEX `idx_dimension`(`dimension` ASC) USING BTREE,
                           INDEX `idx_issue_type`(`issue_type` ASC) USING BTREE,
                           INDEX `idx_priority`(`priority` ASC) USING BTREE,
                           INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 70 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '判责规则表' ROW_FORMAT = Dynamic;

-- ----------------------------


-- ----------------------------
-- Table structure for t_session
-- ----------------------------
DROP TABLE IF EXISTS `t_session`;
CREATE TABLE `t_session`  (
                              `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
                              `chat_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '对话页全局id，同对话多轮共享，单轮由自增主键区分；与 t_chat_memory.chat_id 一致',
                              `user_id` bigint NULL DEFAULT NULL,
                              `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联案例编号（评测时使用）',
                              `channel` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '渠道',
                              `supplier` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '供应商',
                              `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / RUNNING / COMPLETED / FAILED',
                              `total_tokens` int NULL DEFAULT NULL COMMENT '消耗 token 总数',
                              `time_cost` int NULL DEFAULT NULL COMMENT '总耗时（毫秒）',
                              `remark` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
                              `result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最终判责结论（面向用户）',
                              `reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '判责原因（LLM 依据）',
                              `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                              `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人',
                              PRIMARY KEY (`id`) USING BTREE,
                              INDEX `idx_chat_id`(`chat_id` ASC) USING BTREE,
                              INDEX `idx_case_no`(`order_no` ASC) USING BTREE,
                              INDEX `idx_status`(`status` ASC) USING BTREE,
                              INDEX `idx_created_at`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 60 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '判责会话表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------
-- Table structure for t_run_trace
-- ----------------------------
DROP TABLE IF EXISTS `t_run_trace`;
CREATE TABLE `t_run_trace`  (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
                                `session_id` bigint NOT NULL COMMENT '关联 t_session.id（单轮判责记录），token 按单轮汇总',
                                `step` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '执行的步骤',
                                `tool` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '调用的工具',
                                `model` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '调用的AI大模型',
                                `prompt_tokens` bigint NOT NULL COMMENT '输入消耗token数',
                                `completion_tokens` bigint NOT NULL COMMENT '输出消耗token数',
                                `total_tokens` bigint NOT NULL COMMENT '一次对话消耗总token数',
                                `need_token_record` int NULL DEFAULT NULL COMMENT '1: LLM步骤需记录token，0: 非LLM步骤',
                                `time_cost` int NOT NULL COMMENT '对话消耗时间(ms)',
                               `detail` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '步骤摘要：候选规则/firstBadHop/守卫结论等',
                                `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`) USING BTREE,
                                INDEX `idx_session_id`(`session_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 454890584492363777 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '对话Token消耗表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------
-- Table structure for t_chat_memory
-- ----------------------------
DROP TABLE IF EXISTS `t_chat_memory`;
CREATE TABLE `t_chat_memory`  (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
                                `chat_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对话页全局id，即 Spring AI conversationId，与 t_session.chat_id 一致',
                                `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '消息内容（LLM 内部记忆：prompt/结构化输出，不直接展示给用户）',
                                `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息类型：USER / ASSISTANT / SYSTEM / TOOL',
                                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `sequence_id` int NOT NULL COMMENT '会话内单调序号，由 DB MAX+1 生成',
                                `completed` tinyint NOT NULL DEFAULT 0 COMMENT '完结标记：1=属于已完结订单问题（问答对成对打标），压缩只读 completed=0 的活跃消息',
                                PRIMARY KEY (`id`) USING BTREE,
                                INDEX `idx_chat_id`(`chat_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '对话记忆表（CustomJdbcChatMemoryRepository）' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;


-- ----------------------------
-- Table structure for t_user
-- ----------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user`  (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
                           `userAccount` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账号',
                           `userPassword` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
                           `userName` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户昵称',
                           `userAvatar` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户头像',
                           `userProfile` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户简介',
                           `userRole` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT '用户角色：user/admin',
                           `editTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '编辑时间',
                           `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           `isDelete` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除',
                           PRIMARY KEY (`id`) USING BTREE,
                           UNIQUE INDEX `uk_userAccount`(`userAccount` ASC) USING BTREE,
                           INDEX `idx_userName`(`userName` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 454890584492363777 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------
-- Table structure for t_order
-- ----------------------------
DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order`  (
                          `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
                          `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号，MT开头+8位数字',
                          `channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道，如携程/去哪儿/飞猪',
                          `supplier` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商，如美团/艺龙/Agoda',
                          `supplier_source` json NULL COMMENT '供应商源数据/确认单快照 {字段键:值}',
                          `our_transfer` json NULL COMMENT '我司映射后下发给渠道的数据快照',
                          `channel_display` json NULL COMMENT '渠道页面展示快照；null=整跳证据缺失(EVIDENCE_GAP)',
                          `customer_actual` json NULL COMMENT '客人实际体验基线快照；判责时以投诉抽取值覆盖',
                          `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          PRIMARY KEY (`id`) USING BTREE,
                          UNIQUE INDEX `uk_order_no`(`order_no` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '订单四跳证据表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
