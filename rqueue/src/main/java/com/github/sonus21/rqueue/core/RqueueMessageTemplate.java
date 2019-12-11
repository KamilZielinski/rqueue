/*
 * Copyright 2019 Sonu Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.sonus21.rqueue.core;

import static com.github.sonus21.rqueue.core.RedisScriptFactory.getScript;
import static com.github.sonus21.rqueue.utils.QueueInfo.getChannelName;
import static com.github.sonus21.rqueue.utils.QueueInfo.getProcessingQueueChannelName;
import static com.github.sonus21.rqueue.utils.QueueInfo.getProcessingQueueName;
import static com.github.sonus21.rqueue.utils.QueueInfo.getTimeQueueName;

import com.github.sonus21.rqueue.core.RedisScriptFactory.ScriptType;
import com.github.sonus21.rqueue.utils.QueueInfo;
import com.github.sonus21.rqueue.utils.RqueueRedisTemplate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.script.DefaultScriptExecutor;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.CollectionUtils;

@SuppressWarnings("unchecked")
public class RqueueMessageTemplate extends RqueueRedisTemplate<RqueueMessage> {
  private static final int MESSAGE_BATCH_SIZE = 100;
  private DefaultScriptExecutor<String> scriptExecutor;

  public RqueueMessageTemplate(RedisConnectionFactory redisConnectionFactory) {
    super(redisConnectionFactory);
    scriptExecutor = new DefaultScriptExecutor<>(redisTemplate);
  }

  public void add(String queueName, RqueueMessage message) {
    redisTemplate.opsForList().rightPush(queueName, message);
  }

  public RqueueMessage pop(String queueName) {
    long currentTime = System.currentTimeMillis();
    RedisScript<RqueueMessage> script =
        (RedisScript<RqueueMessage>) getScript(ScriptType.REMOVE_MESSAGE);
    return scriptExecutor.execute(
        script,
        Arrays.asList(
            queueName, getProcessingQueueName(queueName), getProcessingQueueChannelName(queueName)),
        currentTime,
        QueueInfo.getMessageReEnqueueTime(currentTime));
  }

  public void addWithDelay(String queueName, RqueueMessage rqueueMessage) {
    long queuedTime = rqueueMessage.getQueuedTime();
    RedisScript<Long> script = (RedisScript<Long>) getScript(ScriptType.ADD_MESSAGE);
    scriptExecutor.execute(
        script,
        Arrays.asList(getTimeQueueName(queueName), getChannelName(queueName)),
        rqueueMessage,
        rqueueMessage.getProcessAt(),
        queuedTime);
  }

  public void removeFromZset(String zsetName, RqueueMessage rqueueMessage) {
    redisTemplate.opsForZSet().remove(zsetName, rqueueMessage);
  }

  public void replaceMessage(String zsetName, RqueueMessage src, RqueueMessage tgt) {
    RedisScript<Long> script = (RedisScript<Long>) getScript(ScriptType.REPLACE_MESSAGE);
    scriptExecutor.execute(script, Collections.singletonList(zsetName), src, tgt);
  }

  public List<RqueueMessage> getAllMessages(String queueName) {
    List<RqueueMessage> messages = redisTemplate.opsForList().range(queueName, 0, -1);
    if (CollectionUtils.isEmpty(messages)) {
      messages = new ArrayList<>();
    }
    Set<RqueueMessage> messagesFromZset =
        redisTemplate.opsForZSet().range(QueueInfo.getTimeQueueName(queueName), 0, -1);
    if (!CollectionUtils.isEmpty(messagesFromZset)) {
      messages.addAll(messagesFromZset);
    }
    Set<RqueueMessage> messagesInProcessingQueue =
        redisTemplate.opsForZSet().range(QueueInfo.getProcessingQueueName(queueName), 0, -1);
    if (!CollectionUtils.isEmpty(messagesInProcessingQueue)) {
      messages.addAll(messagesInProcessingQueue);
    }
    return messages;
  }

  public Long getListLength(String lName) {
    return redisTemplate.opsForList().size(lName);
  }

  public Long getZsetSize(String zsetName) {
    return redisTemplate.opsForZSet().size(zsetName);
  }

  public boolean moveMessage(String srcQueueName, String dstQueueName, int maxMessage) {
    RedisScript<Long> script = (RedisScript<Long>) getScript(ScriptType.MOVE_MESSAGE);
    int offset = MESSAGE_BATCH_SIZE;
    while (true) {
      long remainingMessages =
          scriptExecutor.execute(
              script, Arrays.asList(srcQueueName, dstQueueName), MESSAGE_BATCH_SIZE);
      if (remainingMessages <= 0 || offset >= maxMessage) {
        break;
      }
      offset += MESSAGE_BATCH_SIZE;
    }
    return true;
  }
}
