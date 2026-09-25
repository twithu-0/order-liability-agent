package com.twithu.orderliabilityagent.custom;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.twithu.orderliabilityagent.entity.ChatMemory;
import com.twithu.orderliabilityagent.mapper.ChatMemoryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CustomJdbcChatMemoryRepository implements ChatMemoryRepository {

    @Resource
    private JdbcTemplate mysqlJdbcTemplate;

    @Resource
    private ChatMemoryMapper chatMemoryMapper;

    public static final Integer MAX_MESSAGE = 10; // 允许最多历史对话上下文

    /**
     * metadata 中承载会话内单调序号的 key。
     * <p>隐含契约：只有 findByConversationId（读回路径）会给消息写入该 key；
     * 框架本轮新生成的 UserMessage/AssistantMessage 不会自带它。
     * saveAll 依据"metadata 是否含该 key"区分【已入库历史消息】与【本轮待追加新消息】。
     * 若将来有其它路径给新消息预置了该 key，此判据会失效。</p>
     */
    private static final String METADATA_KEY = "sequence_id";

    @Override
    public List<String> findConversationIds() {
        return mysqlJdbcTemplate.query("select distinct session_no from t_chat_memory"
                ,(rs, rowNum) -> Objects.requireNonNull(rs.getString(1)));
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {

        List<ChatMemory> memoryList = chatMemoryMapper.selectList(new QueryWrapper<ChatMemory>().eq("session_no", conversationId));
        // 对话顺序 新->旧
        memoryList.sort(Comparator.comparingInt(ChatMemory::getSequenceId).reversed());

        boolean isPaired = false;
        List<Message> addMessageList = new ArrayList<>();
        for (ChatMemory chatMemory : memoryList) {
            MessageType messageType = MessageType.valueOf(chatMemory.getType());
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(METADATA_KEY, chatMemory.getSequenceId());
            Message message = switch (messageType) {
                case USER -> {
                    isPaired = true;
                    yield UserMessage.builder().text(chatMemory.getContent()).metadata(metadata).build();
                }
                case ASSISTANT -> AssistantMessage.builder().content(chatMemory.getContent()).properties(metadata).build();
                case SYSTEM -> SystemMessage.builder().text(chatMemory.getContent()).metadata(metadata).build();
                case TOOL -> null; // 工具中间消息不作为记忆返回
            };
            addMessageList.add(message);
            if (addMessageList.size() >= MAX_MESSAGE && isPaired){
                return new ArrayList<>(addMessageList.reversed());
            }
        }
        return new ArrayList<>(addMessageList.reversed());
    }

    /**
     * chatMemory 的 saveAll() 默认策略是替换对话历史（全量删重插）。
     * 这里改为追加模式：只插入本轮尚未入库的新消息，不删除历史。
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
        if (persistableMessages.size() < messages.size()) {
            log.warn("CustomJdbcChatMemoryRepository 已过滤掉工具调用信息, session_no: {}", conversationId);
        }

        // 基于过滤后的可持久化消息，挑出本轮尚未入库的新消息（见 METADATA_KEY 隐含契约）
        List<Message> addMessageList = persistableMessages.stream()
                .filter(message -> message.getMetadata().get(METADATA_KEY) == null)
                .collect(Collectors.toList());
        if (addMessageList.isEmpty()) {
            return; // 没有新消息需要追加
        }

        // 由 DB 计算会话内起始序号（MAX+1），避免内存态在重启/多实例下撞号；
        // 同一批次内多条新消息用连续递增序号，保证与插入顺序一致。
        int baseSeq = nextSequenceId(conversationId);
        List<Object[]> batchArgs = new ArrayList<>(addMessageList.size());
        for (int i = 0; i < addMessageList.size(); i++) {
            Message message = addMessageList.get(i);
            batchArgs.add(new Object[]{
                    conversationId,
                    message.getText(),
                    message.getMessageType().name(),
                    baseSeq + i
            });
        }
        mysqlJdbcTemplate.batchUpdate(
                "INSERT INTO t_chat_memory " +
                        "(session_no, content, type, create_time, sequence_id) " +
                        "VALUES (?, ?, ?, now(), ?)",
                batchArgs
        );
    }

    /**
     * 查询该会话当前最大 sequence_id + 1；无记录时从 1 开始。
     * 由 DB 生成序号，天然规避进程内状态在重启/多副本下的不一致。
     */
    private int nextSequenceId(String conversationId) {
        Integer max = mysqlJdbcTemplate.query(
                "SELECT COALESCE(MAX(sequence_id), 0) FROM t_chat_memory WHERE session_no = ?",
                rs -> rs.next() ? rs.getInt(1) : 0,
                conversationId);
        return (max == null ? 0 : max) + 1;
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        // 不处理，采取追加模式
        // mysqlJdbcTemplate.update("DELETE FROM t_session WHERE session_no = ?", conversationId);
    }

}