/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.eventhubs

import com.microsoft.azure.eventhubs.{ EventData, EventHubClient }
import org.apache.spark.eventhubs.EventHubsConf
import org.apache.spark.eventhubs.client.ClientConnectionPool
import org.apache.spark.eventhubs.utils.RetryUtils._
import org.apache.spark.sql.ForeachWriter

/**
 * A [[ForeachWriter]] to consume data generated by a StreamingQuery.
 * This [[ForeachWriter]] is used to send the generated data to
 * the Event Hub instance specified in the user-provided [[EventHubsConf]].
 * Each partition will use a new deserialized instance, so you usually
 * should do all the initialization (e.g. opening a connection or
 * initiating a transaction) in the open method.
 *
 * This also uses asynchronous send calls which are retried on failure.
 * The retries happen with exponential backoff.
 *
 * @param ehConf the [[EventHubsConf]] containing the connection string
 *               for the Event Hub which will receive the sent events
 */
case class EventHubsForeachWriter(ehConf: EventHubsConf) extends ForeachWriter[String] {
  var client: EventHubClient = _

  def open(partitionId: Long, version: Long): Boolean = {
    client = ClientConnectionPool.borrowClient(ehConf)
    true
  }

  def process(body: String): Unit = {
    val event = EventData.create(s"$body".getBytes("UTF-8"))
    retryJava(client.send(event), "ForeachWriter")
  }

  def close(errorOrNull: Throwable): Unit = {
    errorOrNull match {
      case t: Throwable => throw t
      case _ =>
        ClientConnectionPool.returnClient(ehConf, client)
    }
  }
}