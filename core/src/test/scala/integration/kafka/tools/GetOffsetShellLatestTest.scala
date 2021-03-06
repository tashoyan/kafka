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

class GetOffsetShellLatestTest extends IntegrationTestHarness {

  val producerCount = 1
  val consumerCount = 0
  val serverCount = 1

  val topic1 = "topic1"
  val topic2 = "topic2"

  @Before
  override def setUp: Unit = {
    super.setUp
    TestUtils.createTopic(zkUtils = this.zkUtils,
      topic = topic1,
      numPartitions = 3,
      servers = this.servers)
    TestUtils.createTopic(zkUtils = this.zkUtils,
      topic = topic2,
      numPartitions = 3,
      servers = this.servers)
  }

  @After
  override def tearDown: Unit = {
    super.tearDown
  }

  @Test
  def oneTopicOnePartitionOneMessage: Unit = {
    val producerOffset = sendRecordsLastOffsets(topic1, 0, 1)
    val offsets = getOffsets(brokerList,
      Set(topic1),
      Set(0),
      timestamp = -1,
      includeInternalTopics = false)

    assertEquals(s"Must have 1 offset entry: $offsets", 1, offsets.size)

    val actualOffset = offsets(new TopicPartition(topic1, 0)).right.get
    assertEquals("Actual offset must be equal to producer offset plus 1", producerOffset + 1, actualOffset)
  }

  @Test
  def oneTopicOnePartitionManyMessages: Unit = {
    val producerOffset = sendRecordsLastOffsets(topic1, 0, 10)
    val offsets = getOffsets(brokerList,
      Set(topic1),
      Set(0),
      timestamp = -1,
      includeInternalTopics = false)

    assertEquals(s"Must have 1 offset entry: $offsets", 1, offsets.size)

    val actualOffset = offsets(new TopicPartition(topic1, 0)).right.get
    assertEquals("Actual offset must be equal to producer offset plus 1", producerOffset + 1, actualOffset)
  }

  @Test
  def oneTopicManyPartitions: Unit = {
    val partitions = 0 to 2
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

  @Test
  def manyTopicsManyPartitions: Unit = {
    val topics = Set(topic1, topic2)
    val partitions = 0 to 2
    val producerOffsets: Map[String, IndexedSeq[Long]] =
      topics.map(topic => topic -> partitions.map(p => sendRecordsLastOffsets(topic, p, 10 + 10 * p))).toMap
    val offsets = getOffsets(brokerList,
      topics,
      partitions.toSet,
      timestamp = -1,
      includeInternalTopics = false)

    assertEquals(s"Must have all offset entries: $offsets", topics.size * partitions.size, offsets.size)

    for (topic <- topics) {
      for (p <- partitions) {
        val actualOffset = offsets(new TopicPartition(topic, p)).right.get
        assertEquals(s"Actual offset for partition $topic:$p must be equal to producer offset plus 1", producerOffsets(topic)(p) + 1, actualOffset)
      }
    }
  }

  @Test
  def nonExistingTopic: Unit = {
    sendRecords(topic1, 0, 1)
    val offsets = getOffsets(brokerList,
      Set("topic999"),
      Set(0),
      timestamp = -1,
      includeInternalTopics = false)

    assertEquals(s"Must have 1 offset entry: $offsets", 1, offsets.size)

    val error = offsets(new TopicPartition("topic999", 0)).left.get
    assertTrue(s"Must return an error about non-existing topic: $error",
      error.toLowerCase.contains("topic not found"))
  }

  @Test
  def nonExistingPartition: Unit = {
    sendRecords(topic1, 0, 1)
    val offsets = getOffsets(brokerList,
      Set(topic1),
      Set(9999),
      timestamp = -1,
      includeInternalTopics = false)

    assertEquals(s"Must have 1 offset entry: $offsets", 1, offsets.size)

    val error = offsets(new TopicPartition(topic1, 9999)).left.get
    assertTrue(s"Must return an error about non-existing partition: $error",
      error.toLowerCase.contains("partition not found"))
  }

  @Test
  def manyTopicsSomeNonExisting: Unit = {
    val topics = Set(topic1, "topic999")
    val partitions = 0 to 2
    val producerOffsets = partitions.map(p => sendRecordsLastOffsets(topic1, p, 10 + 10 * p))
    val offsets = getOffsets(brokerList,
      topics,
      partitions.toSet,
      timestamp = -1,
      includeInternalTopics = false)

    assertEquals(s"Must have ${partitions.size} offset entries for existing topic and one offset entry for non-existing topic: $offsets", partitions.size + 1, offsets.size)

    for (p <- partitions) {
      val actualOffset = offsets(new TopicPartition(topic1, p)).right.get
      assertEquals(s"Actual offset for partition $topic1:$p must be equal to producer offset plus 1", producerOffsets(p) + 1, actualOffset)
    }

    val error = offsets(new TopicPartition("topic999", 0)).left.get
    assertTrue(s"Must return an error about non-existing topic: $error",
      error.toLowerCase.contains("topic not found"))
  }

  @Test
  def oneTopicSomePartitionsExistingSomeNonExisting: Unit = {
    val existingPartitions = 0 to 2
    val nonExistingPartitions = 10 to 13
    val partitions = existingPartitions.toSet ++ nonExistingPartitions
    val producerOffsets = existingPartitions.map(p => sendRecordsLastOffsets(topic1, p, 10 + 10 * p))
    val offsets = getOffsets(brokerList,
      Set(topic1),
      partitions,
      timestamp = -1,
      includeInternalTopics = false)

    assertEquals(s"Must have all offset entries: $offsets", partitions.size, offsets.size)

    for (p <- existingPartitions) {
      val actualOffset = offsets(new TopicPartition(topic1, p)).right.get
      assertEquals(s"Actual offset for partition $topic1:$p must be equal to producer offset plus 1", producerOffsets(p) + 1, actualOffset)
    }

    for (p <- nonExistingPartitions) {
      val error = offsets(new TopicPartition(topic1, p)).left.get
      assertTrue(s"Must return an error about non-existing partition: $error",
        error.toLowerCase.contains("partition not found"))
    }
  }

  @Test
  def manyTopicsSomePartitionsExistingSomeNonExisting: Unit = {
    val topics = Set(topic1, topic2)
    val existingPartitions = 0 to 2
    val nonExistingPartitions = 10 to 13
    val partitions = existingPartitions.toSet ++ nonExistingPartitions
    val producerOffsets: Map[String, IndexedSeq[Long]] =
      topics.map(topic => topic -> existingPartitions.map(p => sendRecordsLastOffsets(topic, p, 10 + 10 * p))).toMap
    val offsets = getOffsets(brokerList,
      topics,
      partitions,
      timestamp = -1,
      includeInternalTopics = false)

    assertEquals(s"Must have all offset entries: $offsets", topics.size * partitions.size, offsets.size)

    for (topic <- topics) {
      for (p <- existingPartitions) {
        val actualOffset = offsets(new TopicPartition(topic, p)).right.get
        assertEquals(s"Actual offset for partition $topic:$p must be equal to producer offset plus 1", producerOffsets(topic)(p) + 1, actualOffset)
      }

      for (p <- nonExistingPartitions) {
        val error = offsets(new TopicPartition(topic, p)).left.get
        assertTrue(s"Must return an error about non-existing partition: $error",
          error.toLowerCase.contains("partition not found"))
      }
    }
  }

  @Test
  def noTopicsNoPartitions: Unit = {
    val topics = Set(topic1, topic2)
    val partitions = 0 to 2
    val producerOffsets: Map[String, IndexedSeq[Long]] =
      topics.map(topic => topic -> partitions.map(p => sendRecordsLastOffsets(topic, p, 10 + 10 * p))).toMap
    val offsets = getOffsets(brokerList,
      Set(),
      Set(),
      timestamp = -1,
      includeInternalTopics = false)

    assertEquals(s"Must have all offset entries: $offsets", topics.size * partitions.size, offsets.size)

    for (topic <- topics) {
      for (p <- partitions) {
        val actualOffset = offsets(new TopicPartition(topic, p)).right.get
        assertEquals(s"Actual offset for partition $topic:$p must be equal to producer offset plus 1", producerOffsets(topic)(p) + 1, actualOffset)
      }
    }
  }

  @Test
  def noTopicsWithPartitions: Unit = {
    val topics = Set(topic1, topic2)
    val partitions = 0 to 1
    val producerOffsets: Map[String, IndexedSeq[Long]] =
      topics.map(topic => topic -> partitions.map(p => sendRecordsLastOffsets(topic, p, 10 + 10 * p))).toMap
    val offsets = getOffsets(brokerList,
      Set(),
      partitions.toSet,
      timestamp = -1,
      includeInternalTopics = false)

    assertEquals(s"Must have all offset entries: $offsets", topics.size * partitions.size, offsets.size)

    for (topic <- topics) {
      for (p <- partitions) {
        val actualOffset = offsets(new TopicPartition(topic, p)).right.get
        assertEquals(s"Actual offset for partition $topic:$p must be equal to producer offset plus 1", producerOffsets(topic)(p) + 1, actualOffset)
      }
    }
  }

  @Test
  def noTopicsWithSomeNonExistingPartitions: Unit = {
    val topics = Set(topic1, topic2)
    val existingPartitions = 0 to 2
    val nonExistingPartitions = 10 to 13
    val partitions = existingPartitions.toSet ++ nonExistingPartitions
    val producerOffsets: Map[String, IndexedSeq[Long]] =
      topics.map(topic => topic -> existingPartitions.map(p => sendRecordsLastOffsets(topic, p, 10 + 10 * p))).toMap
    val offsets = getOffsets(brokerList,
      Set(),
      partitions,
      timestamp = -1,
      includeInternalTopics = false)

    assertEquals(s"Must have all offset entries: $offsets", topics.size * partitions.size, offsets.size)

    for (topic <- topics) {
      for (p <- existingPartitions) {
        val actualOffset = offsets(new TopicPartition(topic, p)).right.get
        assertEquals(s"Actual offset for partition $topic:$p must be equal to producer offset plus 1", producerOffsets(topic)(p) + 1, actualOffset)
      }

      for (p <- nonExistingPartitions) {
        val error = offsets(new TopicPartition(topic, p)).left.get
        assertTrue(s"Must return an error about non-existing partition: $error",
          error.toLowerCase.contains("partition not found"))
      }
    }
  }

  @Test
  def noTopicsNoPartitionsIncludeInternalTopics: Unit = {
    val topics = Set(topic1, topic2)
    val partitions = 0 to 2
    val producerOffsets: Map[String, IndexedSeq[Long]] =
      topics.map(topic => topic -> partitions.map(p => sendRecordsLastOffsets(topic, p, 10 + 10 * p))).toMap
    val offsets = getOffsets(brokerList,
      Set(),
      Set(),
      timestamp = -1,
      includeInternalTopics = true)

    assertTrue(s"Must have offset entries for user topics plus internal topics: $offsets", offsets.size > topics.size * partitions.size)

    for (topic <- topics) {
      for (p <- partitions) {
        val actualOffset = offsets(new TopicPartition(topic, p)).right.get
        assertEquals(s"Actual offset for partition $topic:$p must be equal to producer offset plus 1", producerOffsets(topic)(p) + 1, actualOffset)
      }
    }

    assertTrue("Must contain an entry for consumer offsets topic partition 0", offsets.contains(new TopicPartition("__consumer_offsets", 0)))
  }

  @Test
  def noTopicsWithPartitionsIncludeInternalTopics: Unit = {
    val topics = Set(topic1, topic2)
    val partitions = 0 to 1
    val producerOffsets: Map[String, IndexedSeq[Long]] =
      topics.map(topic => topic -> partitions.map(p => sendRecordsLastOffsets(topic, p, 10 + 10 * p))).toMap
    val offsets = getOffsets(brokerList,
      Set(),
      partitions.toSet,
      timestamp = -1,
      includeInternalTopics = true)

    assertTrue(s"Must have offset entries for user topics plus internal topics: $offsets", offsets.size > topics.size * partitions.size)

    for (topic <- topics) {
      for (p <- partitions) {
        val actualOffset = offsets(new TopicPartition(topic, p)).right.get
        assertEquals(s"Actual offset for partition $topic:$p must be equal to producer offset plus 1", producerOffsets(topic)(p) + 1, actualOffset)
      }
    }

    assertTrue("Must contain an entry for consumer offsets topic partition 0", offsets.contains(new TopicPartition("__consumer_offsets", 0)))
  }

  @Test
  def withTopicsNoPartitions: Unit = {
    val topics = Set(topic1)
    val partitions = 0 to 2
    val producerOffsets: Map[String, IndexedSeq[Long]] =
      topics.map(topic => topic -> partitions.map(p => sendRecordsLastOffsets(topic, p, 10 + 10 * p))).toMap
    val offsets = getOffsets(brokerList,
      topics,
      Set(),
      timestamp = -1,
      includeInternalTopics = false)

    assertEquals(s"Must have all offset entries: $offsets", topics.size * partitions.size, offsets.size)

    for (topic <- topics) {
      for (p <- partitions) {
        val actualOffset = offsets(new TopicPartition(topic, p)).right.get
        assertEquals(s"Actual offset for partition $topic:$p must be equal to producer offset plus 1", producerOffsets(topic)(p) + 1, actualOffset)
      }
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
