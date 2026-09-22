package com.twithu.orderliabilityagent.custom;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.twithu.orderliabilityagent.entity.Session;
import com.twithu.orderliabilityagent.mapper.SessionMapper;
import com.twithu.orderliabilityagent.model.enums.SessionStatus;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

@Component
@Slf4j
public class CustomJdbcChatMemoryRepository implements ChatMemoryRepository {

    @Resource
    private JdbcTemplate mysqlJdbcTemplate;

    @Resource
    private SessionMapper sessionMapper;

    private static final String METADATA_KEY = "create_time";
    @Override
    public List<String> findConversationIds() {
        return mysqlJdbcTemplate.query("select distinct session_no from t_chat_memory"
                ,(rs, rowNum) -> Objects.requireNonNull(rs.getString(1)));
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {

        List<Session> sessionList = sessionMapper.selectList(new QueryWrapper<Session>().eq("session_no", conversationId));
        List<Message> messageList = new ArrayList<>();
        for (Session session : sessionList) {
            MessageType messageType = MessageType.valueOf(session.getType());
            Map<String, Object> metadata = new HashMap<>();// 这里保存初始创建时间
            if (session.getCreateTime() != null){
                metadata.put(METADATA_KEY, LocalDateTime.now());
            }
            switch (messageType) {
                case USER -> messageList.add(UserMessage.builder().text(session.getContent()).metadata(metadata).build());
                case ASSISTANT -> messageList.add(AssistantMessage.builder().content(session.getContent()).properties(metadata).build());
                case SYSTEM -> messageList.add(SystemMessage.builder().text(session.getContent()).metadata(metadata).build());
            }
        }
        return messageList;
    }

    /**
     *  chatMemory 的 saveAll() 策略是替换对话历史
     *  落实在具体实现方法，则表现为先删除再插入全部
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");


        List<Message> persistableMessages = messages.stream()
                .filter(m -> !(m instanceof ToolResponseMessage)
                        && !(m instanceof AssistantMessage am && am.hasToolCalls()))
                .toList();
        if ( persistableMessages.size() < messages.size()) {
            log.warn(
                    "CustomJdbcChatMemoryRepository 已过滤掉工具调用信息, session_no: "
                            + conversationId);
        }

        // 先删除之前的对话信息
        this.deleteByConversationId(conversationId);
        // 再批量插入全部的对话信息
        mysqlJdbcTemplate.batchUpdate(
                "INSERT INTO t_session " +
                        "(session_no, type, content, status, create_time, sequence_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?)"
                , new AddBatchPreparedStatement(conversationId, persistableMessages)
        );
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        mysqlJdbcTemplate.update("DELETE FROM t_session WHERE session_no = ?", conversationId);
    }

    private record AddBatchPreparedStatement(String conversationId,
                                             List<Message> messages) implements BatchPreparedStatementSetter {

        @Override
        public void setValues(PreparedStatement ps, int i) throws SQLException {
            var message = this.messages.get(i);
            ps.setString(1, conversationId);
            ps.setString(2, message.getMessageType().name());
            ps.setString(3, message.getText());
            ps.setString(4, SessionStatus.PENDING.getCode());
            ps.setObject(5, message.getMetadata().get(METADATA_KEY) == null ? LocalDateTime.now() : message.getMetadata().get(METADATA_KEY));
            ps.setLong(6, i + 1);

        }

        @Override
        public int getBatchSize() {
            return this.messages.size();
        }
    }

}
