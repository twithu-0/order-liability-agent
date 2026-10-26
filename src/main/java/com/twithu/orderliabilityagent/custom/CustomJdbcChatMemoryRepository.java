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

    /**
     * metadata 中承载完结标记的 key。
     * 完结轮次的 USER/ASSISTANT 成对打标（rememberTurn 保证成对追加，不存在半标记状态）；
     * findByConversationId 只读 completed=0，ChatMemory.get 天然只返回活跃消息，
     * 已完结订单的消息全量保留但不进压缩 prompt。
     */
    public static final String METADATA_COMPLETED = "completed";

    @Override
    public List<String> findConversationIds() {
        return mysqlJdbcTemplate.query("select distinct chat_id from t_chat_memory"
                ,(rs, rowNum) -> Objects.requireNonNull(rs.getString(1)));
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {

        // 活跃视图：只读未完结消息（completed=0）。已完结轮次成对打标后全量保留在表中，
        // 但从此退出 ChatMemory.get 的读取范围，压缩/上下文只见活跃问题。
        // 过滤在查询层做，窗口截断只作用于活跃消息，完结轮次不占窗口名额；
        // 活跃消息必为完整问答对（rememberTurn 成对追加+成对打标），下方配对/截断逻辑不受影响。
        // saveAll 的追加契约同样兼容：add 合并时读到的历史不含完结行，完结行永不被触碰。
        List<ChatMemory> memoryList = chatMemoryMapper.selectList(new QueryWrapper<ChatMemory>()
                .eq("chat_id", conversationId)
                .eq("completed", 0));
        // 对话顺序 新->旧
        memoryList.sort(Comparator.comparingInt(ChatMemory::getSequenceId).reversed());

        boolean isPaired = false;
        List<Message> addMessageList = new ArrayList<>();
        for (ChatMemory chatMemory : memoryList) {
            MessageType messageType = MessageType.valueOf(chatMemory.getType());
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(METADATA_KEY, chatMemory.getSequenceId());
            metadata.put(METADATA_COMPLETED, Integer.valueOf(1).equals(chatMemory.getCompleted()));
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
            log.warn("CustomJdbcChatMemoryRepository 已过滤掉工具调用信息, chat_id: {}", conversationId);
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
                    baseSeq + i,
                    Boolean.TRUE.equals(message.getMetadata().get(METADATA_COMPLETED)) ? 1 : 0
            });
        }
        mysqlJdbcTemplate.batchUpdate(
                "INSERT INTO t_chat_memory " +
                        "(chat_id, content, type, create_time, sequence_id, completed) " +
                        "VALUES (?, ?, ?, now(), ?, ?)",
                batchArgs
        );
    }

    /**
     * 查询该会话当前最大 sequence_id + 1；无记录时从 1 开始。
     * 由 DB 生成序号，天然规避进程内状态在重启/多副本下的不一致。
     */
    private int nextSequenceId(String conversationId) {
        Integer max = mysqlJdbcTemplate.query(
                "SELECT COALESCE(MAX(sequence_id), 0) FROM t_chat_memory WHERE chat_id = ?",
                rs -> rs.next() ? rs.getInt(1) : 0,
                conversationId);
        return (max == null ? 0 : max) + 1;
    }

    /**
     * 完结闭环：把该对话当前所有活跃消息一并打标完结。
     * 语义：压缩器读到的活跃消息就是喂进本次判决的全部上下文，判决出具=这段上下文整体使命结束。
     * 只标当前轮会留下"已完结订单的残缺追问"仍活跃的脏状态，故闭环范围是整个活跃视图。
     * 由 rememberTurn(completed=true) 在写入本轮完结对之前调用；数据全量保留，仅标记变更。
     * 隐含假设：一个窗口同时只有一个活跃问题（串行缺槽补齐）；交叉提问的精确闭环需按订单号打标，见待办。
     */
    public void markActiveCompleted(String chatId) {
        mysqlJdbcTemplate.update(
                "UPDATE t_chat_memory SET completed = 1 WHERE chat_id = ? AND completed = 0", chatId);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        // 不处理，采取追加模式
        // mysqlJdbcTemplate.update("DELETE FROM t_chat_memory WHERE chat_id = ?", conversationId);
    }

}
