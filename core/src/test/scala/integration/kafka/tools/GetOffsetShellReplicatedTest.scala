/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package integration.kafka.tools

import kafka.api.IntegrationTestHarness
import kafka.tools.GetOffsetShell.getOffsets
import kafka.utils.TestUtils
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition
import org.junit.Assert._
import org.junit.{After, Before, Test}

class GetOffsetShellReplicatedTest extends IntegrationTestHarness {

  val producerCount = 1
  val consumerCount = 0
  val serverCount = 2

  val topic1 = "topic1"

  @Before
  override def setUp: Unit = {
    super.setUp
    val leaders = TestUtils.createTopic(zkUtils = this.zkUtils,
      topic = topic1,
      numPartitions = 2,
      replicationFactor = 2,
      servers = this.servers)
    assertEquals("Size of leaders map", 2, leaders.size)
    val p0Leader = leaders(0)
    val p1Leader = leaders(1)
    assertNotEquals("Partitions 0 and 1 are supposed to have different leaders", p0Leader, p1Leader)
  }

  @After
  override def tearDown: Unit = {
    super.tearDown
  }

  @Test
  def twoReplicatedPartitions: Unit = {
    val partitions = 0 to 1
    val producerOffsets = partitions.map(p => sendRecordsLastOffsets(topic1, p, 10 + 10 * p))
    val offsets = getOffsets(brokerList,
      Set(topic1),
      partitions.toSet,
      timestamp = -1,
      includeInternalTopics = false)

    assertEquals(s"Must have all offset entries: $offsets", partitions.size, offsets.size)

    for (p <- partitions) {
      val actualOffset = offsets(new TopicPartition(topic1, p)).right.get
      assertEquals(s"Actual offset for partition $topic1:$p must be equal to producer offset plus 1", producerOffsets(p) + 1, actualOffset)
    }
  }

  private def sendRecordsLastOffsets(topic: String, partition: Int, number: Int): Long = {
    sendRecords(topic, partition, number).last.offset
  }

  private def sendRecords(topic: String, partition: Int, number: Int): Seq[RecordMetadata] = {
    val futures = (0 until number) map { i =>
      val record = new ProducerRecord(topic, partition, i.toString.getBytes, i.toString.getBytes)
      producers.head.send(record)
    }
    futures.map(_.get)
  }
}
